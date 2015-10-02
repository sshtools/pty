package c;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;
/**
 * Structure describing a signal stack (obsolete).<br>
 * <i>native declaration : bits/sigstack.h:651</i><br>
 * This file was autogenerated by <a href="http://jnaerator.googlecode.com/">JNAerator</a>,<br>
 * a tool written by <a href="http://ochafik.com/">Olivier Chafik</a> that <a href="http://code.google.com/p/jnaerator/wiki/CreditsAndLicense">uses a few opensource projects.</a>.<br>
 * For help, please visit <a href="http://nativelibs4java.googlecode.com/">NativeLibs4Java</a> , <a href="http://rococoa.dev.java.net/">Rococoa</a>, or <a href="http://jna.dev.java.net/">JNA</a>.
 */
public class sigstack extends Structure {
	/**
	 * Signal stack pointer.<br>
	 * C type : void*
	 */
	public Pointer ss_sp;
	/** Nonzero if executing on this stack. */
	public int ss_onstack;
	public sigstack() {
		super();
	}
	protected List<? > getFieldOrder() {
		return Arrays.asList("ss_sp", "ss_onstack");
	}
	/**
	 * @param ss_sp Signal stack pointer.<br>
	 * C type : void*<br>
	 * @param ss_onstack Nonzero if executing on this stack.
	 */
	public sigstack(Pointer ss_sp, int ss_onstack) {
		super();
		this.ss_sp = ss_sp;
		this.ss_onstack = ss_onstack;
	}
	public static class ByReference extends sigstack implements Structure.ByReference {
		
	};
	public static class ByValue extends sigstack implements Structure.ByValue {
		
	};
}