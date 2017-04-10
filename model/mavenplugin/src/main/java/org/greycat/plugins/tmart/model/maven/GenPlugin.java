/**
 * Copyright 2017 Ludovic Mouline.  All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.greycat.plugins.tmart.model.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.greycat.plugins.tmart.model.generator.Generator;

import java.io.File;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenPlugin extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/kmf")
    private File targetGen;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/kmfts")
    private File targetGenTs;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/kmfjs")
    private File targetGenJs;

    @Parameter(defaultValue = "${project.basedir}/src")
    private File src;

    @Parameter(defaultValue = "${project.artifactId}")
    private String name;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        //Generate Java
        Generator generator = new Generator();
        try {
            generator.deepScan(src);
        } catch (Exception e) {
            e.printStackTrace();
            throw new MojoExecutionException("Problem during the Scan step");
        }
        generator.generate(name, targetGen);
        project.addCompileSourceRoot(targetGen.getAbsolutePath());

        //todo check if nothing has been broken in the TS/JS generation

        //Generate TS
//        SourceTranslator transpiler = new SourceTranslator(Arrays.asList(targetGen.getAbsolutePath()), targetGenTs.getAbsolutePath(), project.getArtifactId());

//        for (Artifact a : project.getArtifacts()) {
//            File file = a.getFile();
//            if (file != null) {
//                if (file.isFile()) {
//                    transpiler.addToClasspath(file.getAbsolutePath());
//                }
//            }
//        }
//
//
//        transpiler.process();
//        transpiler.addModuleImport("mwg.d.ts");
//        transpiler.generate();

        //Copy mwg.d.ts
//        try {
//            FileWriter mwgLib = new FileWriter(new File(targetGenTs, "mwg.d.ts"));
//            InputStream mwgLibStream = this.getClass().getClassLoader().getResourceAsStream("mwg.d.ts");
//            BufferedReader mwgReader = new BufferedReader(new InputStreamReader(mwgLibStream));
//            String line = mwgReader.readLine();
//            while (line != null) {
//                mwgLib.write(line);
//                mwgLib.write("\n");
//                line = mwgReader.readLine();
//            }
//            mwgLib.flush();
//            mwgLib.close();
//        } catch (IOException ioe) {
//            throw new MojoExecutionException(ioe.getMessage(),ioe);
//        }

        //Copy JS files
        //todo could/should be configurable with filter like include/exclude (?)
        //todo separate debug/prroduction files (in 2 different folders?)
//        try {
//            String[] jsFiles = new String[]{"mwg.js","mwg.min.js"};
//            targetGenJs.mkdirs();
//            for(int idxF=0;idxF<jsFiles.length;idxF++) {
//                FileWriter jsLib = new FileWriter(new File(targetGenJs, jsFiles[idxF]));
//                InputStream mwgLibStream = this.getClass().getClassLoader().getResourceAsStream("js/" + jsFiles[idxF]);
//                BufferedReader mwgReader = new BufferedReader(new InputStreamReader(mwgLibStream));
//                String line = mwgReader.readLine();
//                while (line != null) {
//                    jsLib.write(line);
//                    jsLib.write("\n");
//                    line = mwgReader.readLine();
//                }
//                jsLib.flush();
//                jsLib.close();
//            }
//        } catch (Exception e) {
//            throw new MojoExecutionException(e.getMessage(),e);
//        }

        //Generate JS file
//        try {
//            //Install NPM
//            Process tscInstall = Runtime.getRuntime().exec("npm install typescript --prefix " + project.getBuild().getDirectory());
//            int processResult = tscInstall.waitFor();
//            if(processResult != 0) {
//                BufferedReader errorReader = new BufferedReader(new InputStreamReader(tscInstall.getErrorStream()));
//                StringBuilder errorMessage = new StringBuilder();
//                String line = errorReader.readLine();
//                while(line != null) {
//                    errorMessage.append(line);
//                    line = errorReader.readLine();
//                }
//                throw new MojoExecutionException(tscInstall,"Something wrong append during typescript installation",errorMessage.toString());
//            }
//            BufferedReader outReader = new BufferedReader(new InputStreamReader(tscInstall.getInputStream()));
//            String line = outReader.readLine();
//            getLog().info("Local installation of TypeScript");
//            while(line != null) {
//                getLog().info(line);
//                line = outReader.readLine();
//            }

            //Compile TS files into targetGenJs
//            targetGenJs.mkdirs();
//            Process compile = Runtime.getRuntime().exec(project.getBuild().getDirectory() + "/node_modules/.bin/tsc " + targetGenTs.getAbsolutePath() + "/" + project.getArtifactId() + ".ts -out " + targetGenJs + "/" + project.getArtifactId() + ".js");
//            int compileResult = compile.waitFor();
//            if(compileResult != 0) {
//                BufferedReader errorReader = new BufferedReader(new InputStreamReader(compile.getErrorStream()));
//                StringBuilder errorMessage = new StringBuilder();
//                String err = errorReader.readLine();
//                while(err != null) {
//                    errorMessage.append(err);
//                    err = errorReader.readLine();
//                }
//                getLog().error(errorMessage.toString());
//            }
//            BufferedReader compileOut = new BufferedReader(new InputStreamReader(compile.getInputStream()));
//            String outLine = compileOut.readLine();
//            getLog().info("TS Compilation");
//            while(outLine != null) {
//                getLog().info(outLine);
//                outLine = outReader.readLine();
//            }

//        } catch (IOException | InterruptedException e) {
//            throw new MojoExecutionException(e.getMessage(),e);
//        }

    }

}
