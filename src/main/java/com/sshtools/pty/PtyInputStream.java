package com.sshtools.pty;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;

class PtyInputStream extends InputStream {
	final static Log log = LogFactory.getLog(PtyInputStream.class);
	private final PtyProcess ptyProcess;
	private XCLibrary instance;

	PtyInputStream(PtyProcess ptyProcess) {
		this.ptyProcess = ptyProcess;
		this.instance = XCLibrary.INSTANCE;
	}

	public int read() throws IOException {
		throw new UnsupportedOperationException();
	}

	public int read(byte[] bytes, int arrayOffset, int byteCount)
			throws IOException {
		int n = 0;
		while ((n = doRead(this.ptyProcess.fd, bytes, arrayOffset, byteCount)) < 0) {
			if (log.isDebugEnabled()) {
				log.debug(" read " + n);
			}
			if (n != -XCLibrary.EINTR) {
				throw new IOException("read(" + this.ptyProcess.fd
						+ ", buffer, " + arrayOffset + ", " + byteCount
						+ ") failed: " + String.valueOf(-n));
			}
		}
		if (log.isDebugEnabled()) {
			log.debug("read tot " + n);
		}
		return n;
	}

	int doRead(int fd, byte[] buffer, int bufferOffset, int byteCount) {
		if (byteCount == 0) {
			return 0;
		}
		Memory ptr = new Memory(byteCount);
		int bytesTransferred = instance
				.read(fd, ptr, new NativeLong(byteCount)).intValue();

		if (bytesTransferred > 0) {
			ptr.read(0, buffer, bufferOffset, bytesTransferred);
		}

		return PtyProcess.resultOrMinusErrno(bytesTransferred);
	}
}