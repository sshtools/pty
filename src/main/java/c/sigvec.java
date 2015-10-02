package c;
import c.CLibrary.__sighandler_t;
import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;
/**
 * Structure passed to `sigvec'.<br>
 * <i>native declaration : signal.h:544</i><br>
 * This file was autogenerated by <a href="http://jnaerator.googlecode.com/">JNAerator</a>,<br>
 * a tool written by <a href="http://ochafik.com/">Olivier Chafik</a> that <a href="http://code.google.com/p/jnaerator/wiki/CreditsAndLicense">uses a few opensource projects.</a>.<br>
 * For help, please visit <a href="http://nativelibs4java.googlecode.com/">NativeLibs4Java</a> , <a href="http://rococoa.dev.java.net/">Rococoa</a>, or <a href="http://jna.dev.java.net/">JNA</a>.
 */
public class sigvec extends Structure {
	/**
	 * Signal handler.<br>
	 * C type : __sighandler_t
	 */
	public __sighandler_t sv_handler;
	/** Mask of signals to be blocked. */
	public int sv_mask;
	/** Flags (see below). */
	public int sv_flags;
	public sigvec() {
		super();
	}
	protected List<? > getFieldOrder() {
		return Arrays.asList("sv_handler", "sv_mask", "sv_flags");
	}
	/**
	 * @param sv_handler Signal handler.<br>
	 * C type : __sighandler_t<br>
	 * @param sv_mask Mask of signals to be blocked.<br>
	 * @param sv_flags Flags (see below).
	 */
	public sigvec(__sighandler_t sv_handler, int sv_mask, int sv_flags) {
		super();
		this.sv_handler = sv_handler;
		this.sv_mask = sv_mask;
		this.sv_flags = sv_flags;
	}
	public static class ByReference extends sigvec implements Structure.ByReference {
		
	};
	public static class ByValue extends sigvec implements Structure.ByValue {
		
	};
}