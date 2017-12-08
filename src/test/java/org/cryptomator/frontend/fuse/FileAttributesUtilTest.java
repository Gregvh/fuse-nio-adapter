package org.cryptomator.frontend.fuse;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import ru.serce.jnrfuse.struct.FileStat;

public class FileAttributesUtilTest {

	@ParameterizedTest
	@MethodSource("filePermissionProvider")
	public void testOctalModeToPosixPermissions(Set<PosixFilePermission> expectedPerms, long octalMode) {
		FileAttributesUtil util = new FileAttributesUtil(0, 0);
		Set<PosixFilePermission> perms = util.octalModeToPosixPermissions(octalMode);
		Assertions.assertEquals(expectedPerms, perms);
	}

	static Stream<Arguments> filePermissionProvider() {
		return Stream.of( //
				Arguments.of(PosixFilePermissions.fromString("rwxr-xr-x"), 0755l), //
				Arguments.of(PosixFilePermissions.fromString("---------"), 0000l), //
				Arguments.of(PosixFilePermissions.fromString("r--r--r--"), 0444l), //
				Arguments.of(PosixFilePermissions.fromString("rwx------"), 0700l), //
				Arguments.of(PosixFilePermissions.fromString("rw-r--r--"), 0644l), //
				Arguments.of(PosixFilePermissions.fromString("-w---xr--"), 0214l) //
		);
	}

	@Test
	public void testCopyBasicFileAttributesFromNioToFuse() {
		Instant instant = Instant.ofEpochSecond(424242l, 42);
		FileTime ftime = FileTime.from(instant);
		BasicFileAttributes attr = Mockito.mock(BasicFileAttributes.class);
		Mockito.when(attr.isDirectory()).thenReturn(true);
		Mockito.when(attr.lastModifiedTime()).thenReturn(ftime);
		Mockito.when(attr.creationTime()).thenReturn(ftime);
		Mockito.when(attr.lastAccessTime()).thenReturn(ftime);
		Mockito.when(attr.size()).thenReturn(42l);

		FileAttributesUtil util = new FileAttributesUtil(0, 0);
		FileStat stat = new FileStat(jnr.ffi.Runtime.getSystemRuntime());
		util.copyBasicFileAttributesFromNioToFuse(attr, stat);

		Assertions.assertTrue((FileStat.S_IFDIR & stat.st_mode.intValue()) == FileStat.S_IFDIR);
		Assertions.assertEquals(424242l, stat.st_mtim.tv_sec.get());
		Assertions.assertEquals(42, stat.st_mtim.tv_nsec.intValue());
		Assertions.assertEquals(424242l, stat.st_birthtime.tv_sec.get());
		Assertions.assertEquals(42, stat.st_birthtime.tv_nsec.intValue());
		Assertions.assertEquals(424242l, stat.st_atim.tv_sec.get());
		Assertions.assertEquals(42, stat.st_atim.tv_nsec.intValue());
		Assertions.assertEquals(42l, stat.st_size.longValue());
	}

	@Test
	public void testBasicFileAttributesToFileStat() {
		Instant instant = Instant.ofEpochSecond(424242l, 42);
		FileTime ftime = FileTime.from(instant);
		BasicFileAttributes attr = Mockito.mock(BasicFileAttributes.class);
		Mockito.when(attr.isDirectory()).thenReturn(true);
		Mockito.when(attr.lastModifiedTime()).thenReturn(ftime);
		Mockito.when(attr.creationTime()).thenReturn(ftime);
		Mockito.when(attr.lastAccessTime()).thenReturn(ftime);
		Mockito.when(attr.size()).thenReturn(42l);

		FileAttributesUtil util = new FileAttributesUtil(0, 0);
		FileStat stat = util.basicFileAttributesToFileStat(attr);

		Assertions.assertTrue((FileStat.S_IFDIR & stat.st_mode.intValue()) == FileStat.S_IFDIR);
		Assertions.assertEquals(424242l, stat.st_mtim.tv_sec.get());
		Assertions.assertEquals(42, stat.st_mtim.tv_nsec.intValue());
		Assertions.assertEquals(424242l, stat.st_birthtime.tv_sec.get());
		Assertions.assertEquals(42, stat.st_birthtime.tv_nsec.intValue());
		Assertions.assertEquals(424242l, stat.st_atim.tv_sec.get());
		Assertions.assertEquals(42, stat.st_atim.tv_nsec.intValue());
		Assertions.assertEquals(42l, stat.st_size.longValue());
	}

}