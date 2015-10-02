package com.sshtools.pty;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.IntBuffer;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import c.timeval;
import c.winsize;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;

public class PtyProcess {

	static Log log = LogFactory.getLog(PtyProcess.class);

	public static int resultOrMinusErrno(int result) {
		return result == -1 ? -Native.getLastError() : result;
	}

	int fd = -1;
	private int pid;
	private String slavePtyName;

	private boolean didDumpCore = false;
	private boolean didExitNormally = false;
	private boolean wasSignaled = false;
	private int exitValue;

	private InputStream inStream;
	private OutputStream outStream;
	private XCLibrary instance;

	private final ExecutorService executorService = Executors
			.newSingleThreadExecutor(new DaemonThreadFactory() {
				public String newThreadName() {
					return "Child Forker/Reaper";
				}
			});;

	public boolean wasSignaled() {
		return wasSignaled;
	}

	public boolean didExitNormally() {
		return didExitNormally;
	}

	public int getExitStatus() {
		if (didExitNormally() == false) {
			throw new IllegalStateException("Process did not exit normally.");
		}
		return exitValue;
	}

	public String getPtyName() {
		return slavePtyName;
	}

	public String getSignalDescription() {
		if (wasSignaled() == false) {
			throw new IllegalStateException("Process was not signaled.");
		}

		String signalDescription = String.valueOf(exitValue);
		if (didDumpCore) {
			signalDescription += " --- core dumped";
		}
		return signalDescription;
	}

	public PtyProcess(String executable, String[] argv,
			String workingDirectory, Properties env) throws Exception {
		instance = XCLibrary.INSTANCE;
		start(executable, argv, workingDirectory, env);
		// startProcess(executable, argv, workingDirectory, env);
		inStream = new PtyInputStream(this);
		outStream = new PtyOutputStream(this);
	}

	public InputStream getInputStream() {
		return inStream;
	}

	public OutputStream getOutputStream() {
		return outStream;
	}

	public int getFd() {
		return fd;
	}

	public int getPid() {
		return pid;
	}

	private void startProcess(final String executable, final String[] argv,
			final String workingDirectory, final Properties env)
			throws Exception {
		invoke(new Callable() {
			public Object call() {
				try {
					// nativeStartProcess(executable, argv, workingDirectory);
					start(executable, argv, workingDirectory, env);
					return null;
				} catch (Exception ex) {
					ex.printStackTrace();
					return ex;
				}
			}
		});
	}

	public void waitFor() throws Exception {
		invoke(new Callable() {
			public Object call() {
				try {
					waitFor0();
					return null;
				} catch (Exception ex) {
					return ex;
				}
			}
		});
		executorService.shutdownNow();
	}

	private void waitFor0() throws IOException {
		// FIXME: rewrite this to be more like the JDK's Process.waitFor, both
		// in behavior and implementation.

		// We now have no further use for the fd connecting us to the child,
		// which has probably exited.
		// Even if it hasn't, we're no longer reading its output, which may
		// cause the child to block in the kernel,
		// preventing it from terminating, even if root sends it SIGKILL.
		// If we close the pipe before waiting, then we may let it finish and
		// collect an exit status.
		instance.close(fd);
		fd = -1;

		// Loop until waitpid(2) returns a status or a real error.
		IntBuffer status = IntBuffer.allocate(1);
		int result;
		while ((result = instance.waitpid(pid, status, 0)) < 0) {
			if (result != -XCLibrary.EINTR) {
				// Something really went wrong; give up.
				throw new IOException("waitpid(" + pid + ") failed: "
						+ String.valueOf(-result));
			}
		}

		// TODO Translate the status.
		// if (status.WIFEXITED()) {
		// exitValue = status.WEXITSTATUS();
		// didExitNormally = true;
		// }
		// if (status.WIFSIGNALED()) {
		// exitValue = status.WTERMSIG();
		// wasSignaled = true;
		// didDumpCore = status.WCOREDUMP();
		// }
	}

	/**
	 * Java 1.5.0_03 on Linux 2.4.27 doesn't seem to use LWP threads (according
	 * to ps -eLf) for Java threads. Linux 2.4 is broken such that only the Java
	 * thread which forked a child can wait for it.
	 */
	private void invoke(Callable callable) throws Exception {
		Future future = executorService.submit(callable);
		Exception exception = (Exception) future.get();
		if (exception != null) {
			throw exception;
		}
	}

	public String listProcessesUsingTty() {
		try {
			return nativeListProcessesUsingTty();
		} catch (IOException ex) {
			log.warn("listProcessesUsingTty failed on " + toString() + ".", ex);
			return "";
		}
	}

	public String toString() {
		String result = "JNAPtyProcess[pid=" + pid + ",fd=" + fd + ",pty=\""
				+ slavePtyName + "\"";
		if (didExitNormally) {
			result += ",didExitNormally,exitValue=" + exitValue;
		}
		if (wasSignaled) {
			result += ",wasSignaled,signal=" + exitValue;
		}
		if (didDumpCore) {
			result += ",didDumpCore";
		}
		result += "]";
		return result;
	}

	public void destroy() throws IOException {
		int rc = instance.killpg(pid, XCLibrary.SIGHUP);
		if (rc < 0) {
			throw new IOException("killpg(" + pid + ", SIGHUP) failed: "
					+ String.valueOf(-rc));
		}
	}

	private void start(String executable, String[] argv,
			String workingDirectory, Properties environment) throws IOException {
		PtyGenerator gen = new PtyGenerator();
		fd = gen.openMaster();
		slavePtyName = gen.getSlavePtyName();
		pid = gen.forkAndExec(executable, argv, workingDirectory, environment);
		log.info("Waiting for " + slavePtyName);
		try {
			for(int i = 0; !gen.isSlaveReady() && i < 100; i++) {
				Thread.sleep(100);
			}
		}
		catch(InterruptedException ie) {
			throw new IOException("Interrupted waiting for slave to become ready.", ie);
		}
		if(!gen.isSlaveReady()) {
			throw new IOException("Timeout waiting for slave terminal to be ready.");
		}
//		waitUntilFdWritable(fd);
		log.info("Ready, slave is " + slavePtyName);
	}

	// This seems to interfere with slave creation. Not always, but often.
	// For now I have replaced this with a file marker that is created when
	// slave is ready

//	void waitUntilFdWritable(int fd) throws IOException {
//		int rc;
//		do {
//			fd_set fds = new fd_set();
//			Macros.FD_ZERO(fds);
//			Macros.FD_SET(fd, fds);
//			timeval t = new timeval();
//			t.tv_sec = new NativeLong(10);
//			rc = instance.select(fd + 1, null, fds, null, t);
//		} while (rc == -1 && Native.getLastError() == XCLibrary.EINTR);
//		if (rc == -1) {
//			throw new IOException("select(" + fd + ", ...) failed = "
//					+ Native.getLastError());
//		}
//	}

	public void sendResizeNotification(Dimension sizeInChars,
			Dimension sizeInPixels) throws IOException {
		if (fd == -1) {
			// We shouldn't read or write from a closed pty, but this will
			// happen if the user resizes a window whose child has died.
			// That could just be because they want to read the error message,
			// or because they're fiddling with other tabs.
			return;
		}

		winsize size = new winsize();
		size.ws_col = (short) sizeInChars.width;
		size.ws_row = (short) sizeInChars.height;
		size.ws_xpixel = (short) sizeInPixels.width;
		size.ws_ypixel = (short) sizeInPixels.height;
		if (instance.ioctl(fd, new NativeLong(XCLibrary.TIOCSWINSZ), size) < 0) {
			throw new IOException("ioctl(" + fd + ", TIOCSWINSZ, &size) failed");
		}
	}

	public String nativeListProcessesUsingTty() throws IOException {
		return "";
	}

	private static abstract class DaemonThreadFactory implements ThreadFactory {
		public abstract String newThreadName();

		public Thread newThread(Runnable r) {
			Thread thread = new Thread(r, newThreadName());
			// I was going to make this a run-time option, but right now I
			// can't think why you'd ever want a worker thread to be anything
			// other than a daemon. I don't see why an idle worker thread
			// should be able to prevent the VM from exiting.
			thread.setDaemon(true);
			// Avoiding inheriting the high priority of the event dispatch
			// thread.
			thread.setPriority(Thread.NORM_PRIORITY);
			return thread;
		}
	}

}
