package org.cryptomator.frontend.fuse;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributes;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;

import jnr.ffi.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Timespec;

@PerAdapter
public class ReadWriteFileHandler extends ReadOnlyFileHandler implements Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(ReadWriteFileHandler.class);
	private static final long UTIME_NOW = -1l; // https://github.com/apple/darwin-xnu/blob/xnu-4570.1.46/bsd/sys/stat.h#L538
	private static final long UTIME_OMIT = -2l; // https://github.com/apple/darwin-xnu/blob/xnu-4570.1.46/bsd/sys/stat.h#L539

	@Inject
	public ReadWriteFileHandler(OpenFileFactory openFiles, FileAttributesUtil attrUtil, FileStore fileStore, OpenOptionsUtil openOptionsUtil) {
		super(openFiles, attrUtil, openOptionsUtil);
	}

	@Override
	public int getattr(Path node, BasicFileAttributes attrs, FileStat stat) {
		int result = super.getattr(node, attrs, stat);
		if (result == 0 && attrs instanceof PosixFileAttributes) {
			PosixFileAttributes posixAttrs = (PosixFileAttributes) attrs;
			long mode = attrUtil.posixPermissionsToOctalMode(posixAttrs.permissions());
			stat.st_mode.set(FileStat.S_IFREG | mode);
		} else if (result == 0) {
			stat.st_mode.set(FileStat.S_IFREG | 0777);
		}
		return result;
	}

	public int createAndOpen(Path path, FuseFileInfo fi, FileAttribute<?>... attrs) {
		try {
			long fileHandle = openFiles.open(path, EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE), attrs);
			fi.fh.set(fileHandle);
			return 0;
		} catch (FileAlreadyExistsException e) {
			return -ErrorCodes.EEXIST();
		} catch (IOException e) {
			LOG.error("Error opening file.", e);
			return -ErrorCodes.EIO();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected long open(Path path, Set<OpenOption> openOptions) throws IOException {
		return openFiles.open(path, openOptions);
	}

	public int write(Path path, Pointer buf, long size, long offset, FuseFileInfo fi) {
		OpenFile file = openFiles.get(fi.fh.get());
		if (file == null) {
			LOG.warn("write: File not opened: {}", path);
			return -ErrorCodes.EBADFD();
		}
		try {
			int bytesWritten = file.write(buf, size, offset);
			LOG.trace("Wrote {} bytes to file {}", bytesWritten, path);
			return bytesWritten;
		} catch (IOException e) {
			LOG.error("Writing to file failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	public int flush(Path path, FuseFileInfo fi) {
		OpenFile file = openFiles.get(fi.fh.get());
		if (file == null) {
			LOG.warn("flush: File not opened: {}", path);
			return -ErrorCodes.EBADFD();
		}
		try {
			file.flush();
			return 0;
		} catch (IOException e) {
			LOG.error("Flushing file failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	public int truncate(Path path, long size) {
		try (FileChannel fc = FileChannel.open(path, StandardOpenOption.WRITE)) {
			fc.truncate(size);
			return 0;
		} catch (IOException e) {
			LOG.error("Truncating file feild.", e);
			return -ErrorCodes.EIO();
		}
	}

	public int ftruncate(Path path, long size, FuseFileInfo fi) {
		OpenFile file = openFiles.get(fi.fh.get());
		if (file == null) {
			LOG.warn("ftruncate: File not opened: {}", path);
			return -ErrorCodes.EBADFD();
		}
		try {
			file.truncate(size);
			return 0;
		} catch (IOException e) {
			LOG.error("Flushing file failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	public int utimens(Path node, Timespec mTimeSpec, Timespec aTimeSpec) {
		try {
			FileTime mTime = toFileTime(mTimeSpec);
			FileTime aTime = toFileTime(aTimeSpec);
			Files.getFileAttributeView(node, BasicFileAttributeView.class).setTimes(mTime, aTime, null);
			return 0;
		} catch (DateTimeException | ArithmeticException e) {
			LOG.error("Invalid argument in Instant.ofEpochSecond(...) ", e);
			return -ErrorCodes.EINVAL();
		} catch (NoSuchFileException e) {
			return -ErrorCodes.ENOENT();
		} catch (IOException e) {
			LOG.error("Setting file access/modification times failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	private FileTime toFileTime(Timespec timespec) {
		long seconds = timespec.tv_sec.longValue();
		long nanoseconds = timespec.tv_nsec.longValue();
		if (nanoseconds == UTIME_NOW) {
			return FileTime.from(Instant.now());
		} else if (nanoseconds == UTIME_OMIT) {
			return null;
		} else {
			return FileTime.from(Instant.ofEpochSecond(seconds, nanoseconds));
		}
	}
}
