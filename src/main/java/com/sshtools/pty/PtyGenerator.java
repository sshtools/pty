package com.sshtools.pty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.SystemUtils;

import c.termios;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

class PtyGenerator {

	//
	// DO NOT USE LOGGING OR SYSTEM.OUT FOR DEBUGGING IN HERE, IT MAY INTERFERE
	// AND
	// CAUSE HANGS OR OTHER UNEXPLAINED BEHAVIOR
	//

	private int masterFd;
	private String slavePtyName;
	private XCLibrary instance;

	PtyGenerator() {
		instance = XCLibrary.INSTANCE;
	}

	public int openMaster() throws IOException {
		masterFd = instance.posix_openpt(XCLibrary.O_RDWR | XCLibrary.O_NOCTTY);
		if (masterFd == -1) {
			throw new IOException("posix_openpt(O_RDWR | O_NOCTTY) failed");
		}
		Pointer name = instance.ptsname(masterFd);
		if (name == null) {
			throw new IOException("ptsname(" + masterFd + ") failed");
		}
		slavePtyName = name.getString(0);
		if (getSlaveFile().exists() && !getSlaveFile().delete()) {
			throw new IOException(
					"Failed to delete delete existing slave readyness marker file.");
		}

		if (instance.grantpt(masterFd) != 0) {
			throw new IOException("grantpt(\"" + slavePtyName + "\") failed");
		}
		if (instance.unlockpt(masterFd) != 0) {
			throw new IOException("unlockpt(\"" + slavePtyName + "\") failed");
		}

		// Check that we can open the slave.
		// That's the one failure that in the slave that we cannot otherwise
		// report.
		// When Cygwin runs out of ptys, the slave open failing is the first we
		// get to know about it.
		// close(openSlave());

		return masterFd;
	}

	public int forkAndExec(String executable, String[] argv,
			String workingDirectory, Properties environment) throws IOException {

		writeLog("FORKING: " + executable);
		int pid = instance.fork();
		writeLog("PID: " + pid);
		if (pid < 0) {
			throw new IOException("Fork failed.");
		} else if (pid == 0) {
			try {
				writeLog("FORKED: " + executable);
				runChild(executable, argv, workingDirectory, environment, this);
				writeLog("RETURNED: " + executable);
			} catch (Throwable e) {
				writeError(e);
			}
			instance.exit(1);
			return 0;
		} else {
			return pid;
		}
	}

	int openSlave() throws IOException {
		// The first terminal opened by a System V process becomes its
		// controlling terminal.
		int slaveFd = instance.open(slavePtyName, XCLibrary.O_RDWR);
		if (slaveFd == -1) {
			throw new IOException(
					"open(\""
							+ slavePtyName
							+ "\", O_RDWR) failed - did you run out of pseudo-terminals?");
		}
		return slaveFd;
	}

	void runChild(String executable, String[] argv, String workingDirectory,
			Properties environment, PtyGenerator ptyGenerator)
			throws IOException {
	    
	    System.out.close();
        System.in.close();
        System.err.close();

		writeLog("runChild " + executable);
		if (workingDirectory != null && workingDirectory.length() != 0) {
			if (instance.chdir(workingDirectory) == -1) {
				throw new IOException("chdir(\"" + workingDirectory + "\")");
			}
			writeLog("Chdired to " + workingDirectory);
		}

		// A process relinquishes its controlling terminal when it creates a new
		// session with the setsid(2) function.
		writeLog("Setting sid");
		int setsid = instance.setsid();
		if (setsid == -1) {
			throw new IOException("setsid()");
		}
		writeLog("Set sid is " + setsid);

		int childFd = ptyGenerator.openSlave();
		writeLog("Child fd is " + childFd);
		writeLog("Closing master fd is " + ptyGenerator.masterFd);
		instance.close(ptyGenerator.masterFd);

		if (!SystemUtils.IS_OS_SOLARIS) {
			// The BSD approach is that the controlling terminal for a session
			// is allocated by the session leader by issuing the TIOCSCTTY
			// ioctl.
			// Solaris' termios.h 1.42 now includes a TIOCSCTTY definition,
			// resulting in inappropriate ioctl errors.
			// Once upon a time, there was a suggestion in a comment that
			// earlier SunOS versions might need to avoid this too.
			// This code is believed unnecessary (now we get O_NOCTTY right) but
			// harmless on Cygwin and Linux.
			// We need to use this code on Mac OS, or we get an inappropriate
			// ioctl error from the immediately following tcgetpgrp.
			// APUE says that FreeBSD needs it too.
			if (instance.ioctl(childFd, new NativeLong(XCLibrary.TIOCSCTTY), 0) == -1) {
				throw new IOException("ioctl(" + childFd + ", TIOCSCTTY, 0)");
			}
		}
		int terminalProcessGroup = instance.tcgetpgrp(childFd);
		if (terminalProcessGroup == -1) {
			throw new IOException("tcgetpgrp(" + childFd + ")");
		}
		writeLog("Terminal process group " + terminalProcessGroup);
		if (terminalProcessGroup != instance.getpid()) {
			Native.setLastError(0); // We're abusing unix_exception here.
			throw new IOException("tcgetpgrp(" + childFd + ") ("
					+ terminalProcessGroup + ") != getpid() ("
					+ instance.getpid() + ")");
		}

		if (SystemUtils.IS_OS_SOLARIS) {
			// This seems to be necessary on Solaris to make STREAMS behave.
			instance.ioctl(childFd, new NativeLong(XCLibrary.I_PUSH), "ptem");
			instance.ioctl(childFd, new NativeLong(XCLibrary.I_PUSH), "ldterm");
			instance.ioctl(childFd, new NativeLong(XCLibrary.I_PUSH),
					"ttcompat");
		}

		writeLog("Getting terminal attributes");
		termios terminalAttributes = new termios();
		if (instance.tcgetattr(childFd, terminalAttributes) != 0) {
			throw new IOException("tcgetattr(" + childFd
					+ ", &terminalAttributes)");
		}
		writeLog("Got terminal attributes");
		// Humans don't need XON/XOFF flow control of output, and it only serves
		// to confuse those who accidentally hit ^S or ^Q, so turn it off.
		terminalAttributes.c_iflag &= ~XCLibrary.IXON;
		// Assume input is UTF-8; this allows character-erase to be correctly
		// performed in cooked mode.
		terminalAttributes.c_iflag |= XCLibrary.IUTF8;

		// The equivalent of stty erase ^? for the benefit of Cygwin-1.5, which
		// defaults to ^H.
		// Our belief is that every other platform we care about, including
		// Cygwin-1.7, uses ^?.
		// We used to send ^H on Windows.
		// That worked OK with ssh in Cygwin-1.5 because ssh passes stty erase
		// to the remote system.
		// Only "OK" because it hid the Emacs help, which someone once
		// complained about to the mailing list.
		// It didn't work so well with Cygwin telnet, which neither translated
		// ^H to ^?, nor passed stty erase.
		// One reason I appear to have thought it necessary to send ^H is for
		// the benefit of native Windows applications, writing:
		// "Windows's ReadConsoleInput function always provides applications, like jdb, with ^H, so that's what they expect."
		// However, ReadConsoleInput seems never to return when a Cygwin pty is
		// providing the input, even in 1.5, so I now think that's irrelevant.
		// Search the change log for "backspace" for more information.
		terminalAttributes.c_cc[XCLibrary.VERASE] = 127;

		writeLog("Setting terminal attributes");
		if (instance.tcsetattr(childFd, XCLibrary.TCSANOW, terminalAttributes) != 0) {
			throw new IOException("tcsetattr(" + childFd
					+ ", TCSANOW, &terminalAttributes) with IXON cleared");
		}

		//
		// NOTE: Any logging past this point will come out on the terminal as
		// stdin, stdiout, stderr
		// are now attached to the process
		//

		// Slave becomes stdin/stdout/stderr of child.
		writeLog("Setterminal attributes, checking fds");
		if (childFd != XCLibrary.STDIN_FILENO
				&& instance.dup2(childFd, XCLibrary.STDIN_FILENO) != XCLibrary.STDIN_FILENO) {
			throw new IOException("dup2(" + childFd + ", STDIN_FILENO)");
		}
		writeLog("Checking another FD");
		if (childFd != XCLibrary.STDOUT_FILENO
				&& instance.dup2(childFd, XCLibrary.STDOUT_FILENO) != XCLibrary.STDOUT_FILENO) {
			throw new IOException("dup2(" + childFd + ", STDOUT_FILENO)");
		}
		writeLog("Checking final FD");
		if (childFd != XCLibrary.STDERR_FILENO
				&& instance.dup2(childFd, XCLibrary.STDERR_FILENO) != XCLibrary.STDERR_FILENO) {
			throw new IOException("dup2(" + childFd + ", STDERR_FILENO)");
		}
		writeLog("Closing descriptors");
		closeFileDescriptors();
		writeLog("Fixing environment");
		fixEnvironment(environment);

		// rxvt resets these signal handlers, and we'll do the same, because it
		// magically
		// fixes the bug where ^c doesn't work if we're launched from KDE or
		// Gnome's
		// launcher program. I don't quite understand why - maybe bash reads the
		// existing
		// SIGINT setting, and if it's set to something other than DFL it lets
		// the parent process
		// take care of job control.

		// David Korn asks us to consider the case where...
		// ...a process has SIGCHLD set to SIG_IGN and then execs a new
		// process. A conforming application would not set SIGCHLD to SIG_IGN
		// since the standard leaves this behavior unspecified. An application
		// that does set SIGCHLD to SIG_IGN should set it back to SIG_DFL
		// before the call to exec.
		// http://www.pasc.org/interps/unofficial/db/p1003.1/pasc-1003.1-132.html

		 writeLog("Signalling stuff");
		 instance.signal(XCLibrary.SIGINT, XCLibrary.SIG_DFL);
		 instance.signal(XCLibrary.SIGQUIT, XCLibrary.SIG_DFL);
		 instance.signal(XCLibrary.SIGCHLD, XCLibrary.SIG_DFL);

		writeLog("Executing " + executable);
		getSlaveFile().createNewFile();
		int ret = instance.execvp(executable, argv);
		writeLog("Result " + ret);
		ret = PtyProcess.resultOrMinusErrno(ret);
		if (ret < 0) {
			throw new IOException("Can't execute \"" + executable + "\". "
					+ ret);
		}
	}

	void writeLog(String message) {
		if ("true".equalsIgnoreCase(System.getProperty("pty.debug", "true"))) {
			File f = new File(System.getProperty("java.io.tmpdir")
					+ "/pty.debug");
			try {
				PrintWriter pw = new PrintWriter(new FileOutputStream(f, true));
				try {
					pw.println(String.format("%12d - %s - %s",
							System.currentTimeMillis(), slavePtyName, message));
				} finally {
					pw.close();
				}
			} catch (IOException ioe) {

			}
		}
	}

	void writeError(Throwable exception) {
		if ("true".equalsIgnoreCase(System.getProperty("pty.debug", "true"))) {
			File f = new File(System.getProperty("java.io.tmpdir")
					+ "/pty.debug");
			try {
				PrintWriter pw = new PrintWriter(new FileOutputStream(f, true));
				try {
					exception.printStackTrace(pw);
				} finally {
					pw.close();
				}
			} catch (IOException ioe) {

			}
		}
	}

	void closeFileDescriptors() {
		File dir = new File("/proc/self/fd");
		if (!dir.exists()) {
			dir = new File("/dev/fd");
		}
		if (!dir.exists()) {
			return;
		}
		List<Integer> l = new ArrayList<Integer>();
		for (File f : dir.listFiles()) {
			int fd = Integer.parseInt(f.getName());
			try {
				String path = f.getCanonicalPath();
				if (path.endsWith("/rt.jar") || path.matches(".*/jna.*\\.jar")
						|| path.matches(".*/pty.*\\.jar")) {
					continue;
				}
				// writeLog("File path " + path);
				// if (path.endsWith(".jar") || path.endsWith(".class")) {
				// writeLog("Skipping " + path);
				// continue;
				// }
			} catch (IOException e) {
			}
			if (fd > XCLibrary.STDERR_FILENO) {
				l.add(fd);
			}
		}
		for (Integer i : l) {
			// writeLog("Closing " + i);
			instance.close(i);
		}
	}

	void fixEnvironment(Properties environment) {
		// Tell the world which terminfo entry to use.
		for (Map.Entry<?, ?> ent : environment.entrySet()) {
			// writeLog("Setting " + ent.getKey() + " = " +
			// ent.getValue());
			instance.setenv((String) ent.getKey(), (String) ent.getValue(), 1);

		}
		// According to Thomas Dickey in the XTERM FAQ, some applications that
		// don't use ncurses may need the environment variable $COLORTERM set to
		// realize that they're on a color terminal.
		// Most of the other Unix terminals set it.
		if (environment.containsKey("TERM"))
			instance.setenv("COLORTERM", (String) environment.get("TERM"), 1);

		// X11 terminal emulators set this, but we can't reasonably do so, even
		// on X11.
		// http://elliotth.blogspot.com/2005/12/why-terminator-doesnt-support-windowid.html
		instance.unsetenv("WINDOWID");

		// The JVM sets LD_LIBRARY_PATH, but this upsets some applications.
		// We complained in 2005 (Sun bug 6354700), but there's no sign of
		// progress.
		// FIXME: write the initial value to a system property in
		// "invoke-java.rb" and set it back here?
		instance.unsetenv("LD_LIBRARY_PATH");

		if (SystemUtils.IS_OS_MAC_OSX || SystemUtils.IS_OS_MAC) {
			// Apple's Java launcher uses environment variables to implement the
			// -Xdock options.
			int ppid = instance.getppid();
			instance.unsetenv("APP_ICON_" + ppid);
			instance.unsetenv("APP_NAME_" + ppid);
			instance.unsetenv("JAVA_MAIN_CLASS_" + ppid);

			// Apple's Terminal sets these, and some programs/scripts identify
			// Terminal this way.
			// In real life, these shouldn't be set, but they will be if we're
			// debugging and being run from Terminal.
			// It's always confusing when programs behave differently during
			// debugging!
			instance.unsetenv("TERM_PROGRAM");
			instance.unsetenv("TERM_PROGRAM_VERSION");
		}
	}

	public String getSlavePtyName() {
		return slavePtyName;
	}

	public boolean isSlaveReady() {
		return getSlaveFile().exists();
	}

	private File getSlaveFile() {
		return new File(System.getProperty("java.io.tmpdir") + "/jpty_"
				+ slavePtyName.replace('/', '_') + ".lck");
	}
}