package build.pluto.buildjava.test.simple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.sugarj.common.FileCommands;

import build.pluto.buildjava.JavaBuilder;
import build.pluto.buildjava.JavaInput;
import build.pluto.test.build.ScopedBuildTest;
import build.pluto.test.build.ScopedPath;
import build.pluto.test.build.TrackingBuildManager;

public class MultipleFilesTest extends ScopedBuildTest {

	@ScopedPath(value = "")
	private File sourcePath;

	@ScopedPath(value = "A.java")
	private File classAsource;

	@ScopedPath(value = "B.java")
	private File classBsource;

	@ScopedPath(value = "bin/")
	private File targetDir;

	private TrackingBuildManager build() throws IOException {
		TrackingBuildManager manager = new TrackingBuildManager();
		manager.require(JavaBuilder.request(new JavaInput(classAsource, targetDir, sourcePath)));
		return manager;
	}

	@Test
	public void testBuildClean() throws IOException {
		TrackingBuildManager manager = build();
		assertTrue("No class file for A generated", new File(targetDir, "A.class").exists());
		assertTrue("No class file for B generated", new File(targetDir, "B.class").exists());
		assertTrue("No dep file for A generated", new File(targetDir, "A.dep").exists());
		assertTrue("No dep file for B generated", new File(targetDir, "B.dep").exists());
		assertEquals(2, manager.getExecutedInputs().size());
	}

	@Test
	public void testCleanRebuildDoesNothing() throws IOException {
		build();
		TrackingBuildManager manager = build();
		assertEquals(0, manager.getExecutedInputs().size());
	}

	@Test
	public void testRebuildOnBChange() throws IOException {
		build();
		FileCommands.writeToFile(classBsource, "class B { int x; }");
		TrackingBuildManager manager = build();
		assertEquals("Should compile class A and B", 2, manager.getExecutedInputs().size());
	}

	@Test
	public void testRebuildOnAChange() throws IOException {
		build();
		FileCommands.writeToFile(classAsource, "class A { int x; B b; }");
		TrackingBuildManager manager = build();
		assertEquals("Should any execute calss A", 1, manager.getExecutedInputs().size());
		assertEquals("Should require class A and B", 2, manager.getRequiredInputs().size());
	}

}
