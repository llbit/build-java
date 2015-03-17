package org.sugarj.cleardep.buildjava;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.util.HotSwapper;

import org.sugarj.cleardep.build.BuildRequest;
import org.sugarj.cleardep.build.Builder;
import org.sugarj.cleardep.build.BuilderFactory;
import org.sugarj.cleardep.buildjava.util.JavaCommands;
import org.sugarj.cleardep.buildjava.util.ListUtils;
import org.sugarj.cleardep.output.None;
import org.sugarj.cleardep.stamp.LastModifiedStamper;
import org.sugarj.cleardep.stamp.Stamper;
import org.sugarj.common.FileCommands;
import org.sugarj.common.Log;
import org.sugarj.common.errors.SourceCodeException;
import org.sugarj.common.errors.SourceLocation;
import org.sugarj.common.path.Path;
import org.sugarj.common.path.RelativePath;
import org.sugarj.common.util.Pair;

import com.sun.jdi.connect.IllegalConnectorArgumentsException;

public class JavaBuilder extends Builder<JavaBuilder.Input, None> {

	public static BuilderFactory<Input, None, JavaBuilder> factory = new BuilderFactory<Input, None, JavaBuilder>() {
		private static final long serialVersionUID = 2193786625546374284L;

		@Override
		public JavaBuilder makeBuilder(Input input) {
			return new JavaBuilder(input);
		}
	};

	public static class Input implements Serializable {
		private static final long serialVersionUID = -8905198283548748809L;
		public final List<Path> inputFiles;
		public final Path targetDir;
		public final List<Path> sourcePaths;
		public final List<Path> classPaths;
		public final List<String> additionalArgs;
		public final List<BuildRequest<?, ?, ?, ?>> requiredUnits;
		public final Boolean deepRequire;

		public transient boolean noMetaDependency = false;

		public Input(List<Path> inputFiles, Path targetDir,
				List<Path> sourcePaths, List<Path> classPaths,
				List<String> additionalArgs,
				List<BuildRequest<?, ?, ?, ?>> requiredUnits,
				Boolean deepRequire) {
			this.inputFiles = inputFiles;
			this.targetDir = targetDir;
			this.sourcePaths = sourcePaths != null ? sourcePaths
					: new ArrayList<Path>();
			this.classPaths = classPaths != null ? classPaths
					: new ArrayList<Path>();
			this.additionalArgs = additionalArgs;
			this.requiredUnits = requiredUnits;
			this.deepRequire = deepRequire;

		}
	}

	private JavaBuilder(Input input) {
		super(input);
	}

	@Override
	protected String description() {
		return "Compile Java files " + ListUtils.printList(input.inputFiles);
	}

	@Override
	protected Path persistentPath() {
		if (input.inputFiles.size() == 1) {
			// return new RelativePath(input.targetDir,
			// FileCommands.fileName(input.inputFiles.get(0)) + ".dep");
			return correspondingBinPath(input.inputFiles.get(0)
					.replaceExtension("dep"));
		}

		int hash = Arrays.hashCode(input.inputFiles.toArray());

		return new RelativePath(input.targetDir, "javaFiles" + hash + ".dep");
	}

	private Path correspondingBinPath(Path srcFile) {
		for (Path sourcePath : input.sourcePaths) {
			RelativePath rel = FileCommands
					.getRelativePath(sourcePath, srcFile);
			if (rel != null)
				return new RelativePath(input.targetDir, rel.getRelativePath());
		}
		return input.targetDir;
	}

	@Override
	public Stamper defaultStamper() {
		return LastModifiedStamper.instance;
	}

	@Override
	public None build() throws IOException {
		try {
			requireBuild(input.requiredUnits);
			
			for (Path p : input.inputFiles) {
				require(p);
			}
			Pair<List<Path>, List<Path>> outFiles = JavaCommands.javac(
					input.inputFiles, input.sourcePaths, input.targetDir,
					input.additionalArgs, input.classPaths);
			for (Path outFile : outFiles.a) {
				if (input.deepRequire) {
					RelativePath relP = FileCommands.getRelativePath(
							input.targetDir, outFile.replaceExtension("java"));

					boolean found = false;
					for (Path sourcePath : input.sourcePaths) {
						RelativePath relSP = new RelativePath(sourcePath,
								relP.getRelativePath());
						if (FileCommands.exists(relSP)) {
							found = true;
							if (!input.inputFiles.contains(relSP)) {
								found = false;
								Input newInput = new Input(
										Arrays.asList((Path) relSP),
										input.targetDir, input.sourcePaths,
										input.classPaths, input.additionalArgs,
										input.requiredUnits, input.deepRequire);
								newInput.noMetaDependency = input.noMetaDependency;
								requireBuild(JavaBuilder.factory, newInput);
							}
							break;
						}
					}
					if (found)
						provide(outFile);
				} else {
					provide(outFile);
				}
			}
			for (Path p : outFiles.b) {
				RelativePath relP = FileCommands.getRelativePath(
						input.targetDir, p.replaceExtension("java"));

				if (input.deepRequire && relP != null) {
					boolean found = false;
					if (relP != null)
						for (Path sourcePath : input.sourcePaths) {
							RelativePath relSP = new RelativePath(sourcePath,
									relP.getRelativePath());
							if (FileCommands.exists(relSP)) {
								found = true;
								if (!input.inputFiles.contains(relSP)) {
									found = false;
									Input newInput = new Input(
											Arrays.asList((Path) relSP),
											input.targetDir, input.sourcePaths,
											input.classPaths,
											input.additionalArgs,
											input.requiredUnits,
											input.deepRequire);
									newInput.noMetaDependency = input.noMetaDependency;
									requireBuild(JavaBuilder.factory, newInput);
								}
								break;
							}
						}
					if (found)
						require(p);
				} else {
					require(p);
				}
			}

			if (!input.noMetaDependency) {
				try {
					Log.log.log("Starting hotswap...", Log.CORE);
					HotSwapper hs = new HotSwapper(8000);

					ClassPool cp = ClassPool.getDefault();

					for (Path p : input.inputFiles) {
						RelativePath relPath = FileCommands.getRelativePath(
								input.targetDir, p);
						String fullQualifiedName = relPath.getRelativePath()
								.replace('/', '.');

						CtClass cc = cp.get(fullQualifiedName);
						byte[] byteCode = cc.toBytecode();// loadClassData("org.sugarj.cleardep.build.SomeClass");
						hs.reload(fullQualifiedName, byteCode);
						Log.log.log("Successfully hotswapped: " + fullQualifiedName, Log.CORE);
					}
				} catch (IOException | NullPointerException ex) {
					Log.log.log(
							"Hotswap unsuccessful. Please start the instance with the jvm parameters -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000",
							Log.CORE);
					ex.printStackTrace();
				} catch (IllegalConnectorArgumentsException ex) {

				} catch (NotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (CannotCompileException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} catch (SourceCodeException e) {
			StringBuilder errMsg = new StringBuilder(
					"The following errors occured during compilation:\n");
			for (Pair<SourceLocation, String> error : e.getErrors()) {
				errMsg.append(FileCommands.dropDirectory(error.a.file) + "("
						+ error.a.lineStart + ":" + error.a.columnStart + "): "
						+ error.b);
			}
			throw new IOException(errMsg.toString());
		}
		return None.val;
	}
}
