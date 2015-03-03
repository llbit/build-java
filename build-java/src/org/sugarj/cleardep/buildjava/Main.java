package org.sugarj.cleardep.buildjava;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.sugarj.cleardep.build.BuildManager;
import org.sugarj.cleardep.build.BuildRequest;
import org.sugarj.cleardep.buildjava.util.FileExtensionFilter;
import org.sugarj.common.FileCommands;
import org.sugarj.common.path.AbsolutePath;
import org.sugarj.common.path.Path;
import org.sugarj.common.path.RelativePath;


public class Main {

	public static void main(String[] args) {
		BuildManager manager = BuildManager.acquire();
		
		Path baseDir = new AbsolutePath(args[0]);
		
		try {
			List<Path> files = new ArrayList<Path>();
			for (RelativePath p: FileCommands.listFiles(baseDir, new FileExtensionFilter("java"))) {
				files.add(p);
			}
			List<Path> paths = new ArrayList<>();
			paths.add(baseDir);
			
			List<String> additionalArguments = new ArrayList<String>();
			//additionalArguments.add("-XDignore.symbol.file");
			
			JavaBuilder.Input input = new JavaBuilder.Input(files, new RelativePath(baseDir, "bin"), paths, paths, additionalArguments, null, true);
			manager.require(new BuildRequest<>(JavaBuilder.factory, input));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
