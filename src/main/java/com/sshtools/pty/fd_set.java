package com.sshtools.pty;

import java.util.Arrays;
import java.util.List;

public class fd_set extends c.fd_set {
	public static final int FD_SETSIZE = 1024;
	public static final int NFDBITS = 32 * 8;
	public static final int FD_NFDBITS = NFDBITS;
	public static final int howmany = (((FD_SETSIZE) + ((FD_NFDBITS) - 1)) / (FD_NFDBITS));

	public int[] fds_bits = new int[howmany];

	public void set(int fd) {
		int index = fd / FD_NFDBITS;
		int offset = fd % FD_NFDBITS;

		fds_bits[index] |= (1 << offset);
	}

	public void clr(int fd) {
		int index = fd / FD_NFDBITS;
		int offset = fd % FD_NFDBITS;

		fds_bits[index] &= ~(1 << offset);
	}

	public boolean isSet(int fd) {
		int index = fd / FD_NFDBITS;
		int offset = fd % FD_NFDBITS;

		return (fds_bits[index] & (1 << offset)) != 0;
	}

	public void zero() {
		for (int i = 0; i < fds_bits.length; i++) {
			fds_bits[i] = 0;
		}
	}

	protected List<?> getFieldOrder() {
		return Arrays.asList("fds_bits");
	}
}
