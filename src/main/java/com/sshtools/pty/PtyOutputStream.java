package com.sshtools.pty;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;

class PtyOutputStream extends OutputStream {
	/**
	 * 
	 */
	private final PtyProcess ptyProcess;
	private XCLibrary instance;

	final static Log log = LogFactory.getLog(PtyOutputStream.class);

	PtyOutputStream(PtyProcess ptyProcess) {
		this.ptyProcess = ptyProcess;
		this.instance = XCLibrary.INSTANCE;
	}

	public void write(int b) throws IOException {
		throw new UnsupportedOperationException();
	}

	public void write(byte[] bytes, int arrayOffset, int byteCount)
			throws IOException {
		int offset = arrayOffset;
		int remainingByteCount = byteCount;
		int n = 0;
		while (remainingByteCount > 0) {
			if (log.isDebugEnabled()) {
				log.debug("write " + offset + " , " + byteCount);
			}
			n = doWrite(this.ptyProcess.getFd(), bytes, offset, byteCount);
			if (n < 0 && n != -XCLibrary.EINTR) {
				// This write failed, and not because we were interrupted
				// before writing anything. Give up.
				break;
			}
			if (n > 0) {
				offset += n;
				remainingByteCount -= n;
			}
		}
		if (remainingByteCount != 0) {
			throw new IOException("write(" + this.ptyProcess.fd + ", buffer, "
					+ arrayOffset + ", " + byteCount + ") failed: "
					+ String.valueOf(-n));
		}
	}

	int doWrite(int fd, byte[] buffer, int bufferOffset, int byteCount) {
		if (byteCount == 0) {
			return 0;
		}
		Memory ptr = new Memory(byteCount);
		ptr.write(0, buffer, bufferOffset, byteCount);
		return PtyProcess.resultOrMinusErrno(instance.write(fd, ptr,
				new NativeLong(byteCount)).intValue());
	}
}