package org.sugarj.cleardep.buildjava.util;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.sugarj.common.path.AbsolutePath;
import org.sugarj.common.path.Path;
import org.sugarj.common.path.RelativePath;

/**
 * @author Sebastian Erdweg
 */
public class SugarLangProjectEnvironment {
  public static Environment makeProjectEnvironment(IProject project) {
      IJavaProject javaProject = JavaCore.create(project);
      if (javaProject == null)
        return null;
      
      Environment env = null;
      
      try {
        env = makeProjectEnvironment(javaProject);
      } catch (JavaModelException e) {
        throw new RuntimeException(e);
      }
      
      return env;
    }
    
    private static Environment makeProjectEnvironment(IJavaProject project) throws JavaModelException {
      Environment env = new Environment();
      
      IPath fullPath = project.getProject().getFullPath();
      Path root = new AbsolutePath(project.getProject().getLocation().makeAbsolute().toString());
      Path bin = new RelativePath(root, project.getOutputLocation().makeRelativeTo(fullPath).toString());
      env.setRoot(root);
      env.setBin(bin);
      
      env.setJavaComplianceLevel((String)JavaCore.getOptions().get("org.eclipse.jdt.core.compiler.compliance"));

      // TODO: do we do this here? 
      env.addToIncludePath(bin);
      
      for (IPackageFragmentRoot fragment : project.getAllPackageFragmentRoots()) {
        IPath path = fragment.getPath();
        boolean externalPath = fragment.getResource() == null;
        String p = externalPath ? path.toString() : path.makeRelativeTo(fullPath).toString();

        Path includePath; 
        if (fullPath.isPrefixOf(path))
          includePath = p.isEmpty() ? root : new RelativePath(root, p);
        else if (externalPath)
          includePath = new AbsolutePath(p);
        else
          includePath = new RelativePath(root, p);
        
        if (fragment.getKind() == IPackageFragmentRoot.K_SOURCE && fragment.getParent().equals(project))
          env.addToSourcePath(includePath);
        else if (fragment.getKind() == IPackageFragmentRoot.K_BINARY)
          env.addToIncludePath(includePath);
      }
      
      for (String reqProject : project.getRequiredProjectNames()) {
        IJavaProject reqJavaProject = JavaCore.create(project.getProject().getWorkspace().getRoot().getProject(reqProject));
        if (reqJavaProject != null) {
          Environment projEnv = makeProjectEnvironment(reqJavaProject);
//          env.getSourcePath().addAll(projEnv.getSourcePath());
          env.addToIncludePath(projEnv.getBin());
          
          // XXX due to transitive imports in SDF and Stratego
          for (Path p : projEnv.getIncludePath())
            env.addToIncludePath(p);
        }
      }
      
      return env;
    }
}