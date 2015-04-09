package build.pluto.buildjava.eclipse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaModelException;
import build.pluto.builder.BuildManager;
import build.pluto.builder.BuildRequest;
import build.pluto.buildjava.eclipse.EclipseConsole;
import build.pluto.buildjava.JavaBuilder;
import build.pluto.buildjava.JavaBuilder.Input;
import build.pluto.buildjava.eclipse.Environment;
import build.pluto.buildjava.util.FileExtensionFilter;
import build.pluto.buildjava.eclipse.SugarLangProjectEnvironment;
import build.pluto.output.None;
import org.sugarj.common.FileCommands;
import org.sugarj.common.Log;
import org.sugarj.common.path.Path;

/**
 * updates editors to show newly built results
 * 
 * @author Sebastian Erdweg <seba at informatik uni-marburg de>
 */
public class EclipseJavaBuilder extends IncrementalProjectBuilder {

	protected IProject[] build(int kind, Map<String, String> args,
			IProgressMonitor monitor) {
		System.out.println("Starting build...");

		InitConsole();
		
		try {
			List<JavaBuilder.Input> inputs = makeInputs(getProject());
			@SuppressWarnings("unchecked")
			BuildRequest<?, None, ?, ?>[] reqs = (BuildRequest<?, None, ?, ?>[]) new BuildRequest[inputs.size()];
			int i = 0;
			for (Input input : inputs) {
				reqs[i] = new BuildRequest<>(JavaBuilder.factory, input);
				i++;
			}
			
			BuildManager.buildAll(reqs);
			
			getProject().refreshLocal(IProject.DEPTH_INFINITE, monitor);
		} catch (CoreException e) {
			e.printStackTrace();
		} finally {
			monitor.done();
		}
		return null;
	}
	
	private void InitConsole() {
		Log.out = EclipseConsole.getOutputPrintStream();
		Log.err = EclipseConsole.getErrorPrintStream();
		Log.log.setLoggingLevel(Log.ALWAYS);
		EclipseConsole.activateConsoleOnce();
	}

	private List<Input> makeInputs(IProject project) throws JavaModelException {
		Environment env = SugarLangProjectEnvironment.makeProjectEnvironment(project);

		List<Path> files = new ArrayList<Path>();
		for (Path sp: env.getSourcePath()) {
			for (Path p: FileCommands.listFilesRecursive(sp, new FileExtensionFilter("java"))) {
				files.add(p);
			}
		}
		
		List<Input> inputs = new ArrayList<Input>();
		
		for (Path p: files) {
			JavaBuilder.Input input = new Input(Arrays.asList(p), env.getBin(), env.getSourcePath(),
					env.getIncludePath(), Arrays.asList("-source", env.getJavaComplianceLevel()), null, true);
			inputs.add(input);
		}

		return inputs;
	}

	protected void clean(IProgressMonitor monitor) throws CoreException {
		System.out.println("Starting clean...");
		InitConsole();
		
		Log.log.beginTask("Starting clean", Log.ALWAYS);
		
		Environment env = SugarLangProjectEnvironment.makeProjectEnvironment(getProject());
	
		try {
			FileCommands.delete(env.getBin());
			FileCommands.createDir(env.getBin());
			getProject().refreshLocal(IProject.DEPTH_INFINITE, monitor);
		} catch (IOException e) {
			Log.err.println("Clean failed...");
		}
		monitor.done();
		
		Log.log.endTask();
	}
}