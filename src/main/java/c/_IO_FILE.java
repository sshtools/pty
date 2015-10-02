package c;
import c.CLibrary._IO_lock_t;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;
/**
 * <i>native declaration : libio.h:1508</i><br>
 * This file was autogenerated by <a href="http://jnaerator.googlecode.com/">JNAerator</a>,<br>
 * a tool written by <a href="http://ochafik.com/">Olivier Chafik</a> that <a href="http://code.google.com/p/jnaerator/wiki/CreditsAndLicense">uses a few opensource projects.</a>.<br>
 * For help, please visit <a href="http://nativelibs4java.googlecode.com/">NativeLibs4Java</a> , <a href="http://rococoa.dev.java.net/">Rococoa</a>, or <a href="http://jna.dev.java.net/">JNA</a>.
 */
public abstract class _IO_FILE extends Structure {
	/** High-order word is _IO_MAGIC; rest is flags. */
	public int _flags;
	/**
	 * Note:  Tk uses the _IO_read_ptr and _IO_read_end fields directly.<br>
	 * Current read pointer<br>
	 * C type : char*
	 */
	public Pointer _IO_read_ptr;
	/**
	 * End of get area.<br>
	 * C type : char*
	 */
	public Pointer _IO_read_end;
	/**
	 * Start of putback+get area.<br>
	 * C type : char*
	 */
	public Pointer _IO_read_base;
	/**
	 * Start of put area.<br>
	 * C type : char*
	 */
	public Pointer _IO_write_base;
	/**
	 * Current put pointer.<br>
	 * C type : char*
	 */
	public Pointer _IO_write_ptr;
	/**
	 * End of put area.<br>
	 * C type : char*
	 */
	public Pointer _IO_write_end;
	/**
	 * Start of reserve area.<br>
	 * C type : char*
	 */
	public Pointer _IO_buf_base;
	/**
	 * End of reserve area.<br>
	 * C type : char*
	 */
	public Pointer _IO_buf_end;
	/**
	 * The following fields are used to support backing up and undo.<br>
	 * Pointer to start of non-current get area.<br>
	 * C type : char*
	 */
	public Pointer _IO_save_base;
	/**
	 * Pointer to first valid character of backup area<br>
	 * C type : char*
	 */
	public Pointer _IO_backup_base;
	/**
	 * Pointer to end of non-current get area.<br>
	 * C type : char*
	 */
	public Pointer _IO_save_end;
	/** C type : _IO_marker* */
	public c._IO_marker.ByReference _markers;
	/** C type : _IO_FILE* */
	public _IO_FILE.ByReference _chain;
	public int _fileno;
	public int _flags2;
	/**
	 * This used to be _offset but it's too small.<br>
	 * C type : __off_t
	 */
	public NativeLong _old_offset;
	/** 1+column number of pbase(); 0 is unknown. */
	public short _cur_column;
	public byte _vtable_offset;
	/** C type : char[1] */
	public byte[] _shortbuf = new byte[1];
	/** C type : _IO_lock_t* */
	public _IO_lock_t _lock;
	/** C type : __off64_t */
	public NativeLong _offset;
	/** C type : void* */
	public Pointer __pad1;
	/** C type : void* */
	public Pointer __pad2;
	/** C type : void* */
	public Pointer __pad3;
	/** C type : void* */
	public Pointer __pad4;
	public NativeLong __pad5;
	public int _mode;
	/** Conversion Error : sizeof(int) */
	public _IO_FILE() {
		super();
	}
	protected List<? > getFieldOrder() {
		return Arrays.asList("_flags", "_IO_read_ptr", "_IO_read_end", "_IO_read_base", "_IO_write_base", "_IO_write_ptr", "_IO_write_end", "_IO_buf_base", "_IO_buf_end", "_IO_save_base", "_IO_backup_base", "_IO_save_end", "_markers", "_chain", "_fileno", "_flags2", "_old_offset", "_cur_column", "_vtable_offset", "_shortbuf", "_lock", "_offset", "__pad1", "__pad2", "__pad3", "__pad4", "__pad5", "_mode");
	}
	public static abstract class ByReference extends _IO_FILE implements Structure.ByReference {
		
	};
	public static abstract class ByValue extends _IO_FILE implements Structure.ByValue {
		
	};
}
