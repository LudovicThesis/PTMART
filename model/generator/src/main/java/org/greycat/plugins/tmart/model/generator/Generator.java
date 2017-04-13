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
package org.greycat.plugins.tmart.model.generator;


import greycat.*;
import greycat.base.BaseNode;
import greycat.internal.CoreDeferCounter;
import greycat.plugin.Job;
import greycat.plugin.NodeFactory;
import greycat.struct.Buffer;
import org.greycat.plugins.tmart.model.ast.*;
import org.greycat.plugins.tmart.model.ast.Class;
import org.greycat.plugins.tmart.model.ast.Enum;
import org.greycat.plugins.tmart.model.ast.impl.ModelImpl;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.Visibility;
import org.jboss.forge.roaster.model.source.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Generator {

    public static String extension = ".mm";

    private Model model = new ModelImpl();

    private List<JavaSource> sources;

    public void scan(File target) throws Exception {
        String[] everythingInThisDir = target.list();
        for (String name : everythingInThisDir) {
            if (name.trim().endsWith(extension)) {
                ModelBuilder.parse(new File(target, name), model);
            }
        }
    }

    public void deepScan(File target) throws Exception {
        String[] everythingInThisDir = target.list();
        for (String name : everythingInThisDir) {
            if (name.trim().endsWith(extension)) {
                ModelBuilder.parse(new File(target, name), model);
            } else {
                File current = new File(target, name);
                if (current.isDirectory()) {
                    deepScan(current);
                }
            }
        }
    }

    public void generate(String name, File target) {
        boolean useML = false;

        sources = new ArrayList<JavaSource>();
        //Generate all NodeType
        for (Classifier classifier : model.classifiers()) {
            if (classifier instanceof Enum) {
                Enum loopEnum = (Enum) classifier;
                final JavaEnumSource javaEnum = Roaster.create(JavaEnumSource.class);
                if (classifier.pack() != null) {
                    javaEnum.setPackage(classifier.pack().toLowerCase());
                }
                javaEnum.setName(classifier.name());
                for (String literal : loopEnum.literals()) {
                    javaEnum.addEnumConstant(literal);
                }
                sources.add(javaEnum);
            } else if (classifier instanceof Class) {
                final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
                Class loopClass = (Class) classifier;
                if (classifier.pack() != null) {
                    javaClass.setPackage(classifier.pack().toLowerCase());
                }
                javaClass.setName(classifier.name());

                String parentName = "greycat.base.BaseNode";
                if (loopClass.parent() != null) {
                    parentName = loopClass.parent().fqn();
                }
                javaClass.setSuperType(parentName);

                MethodSource<JavaClassSource> constructor = javaClass.addMethod().setConstructor(true);
                constructor.addParameter("long", "p_world");
                constructor.addParameter("long", "p_time");
                constructor.addParameter("long", "p_id");
                constructor.addParameter(Graph.class, "p_graph");
                constructor.setBody("super(p_world, p_time, p_id, p_graph);");
                constructor.setVisibility(Visibility.PUBLIC);

                //add helper name
                javaClass.addField()
                        .setVisibility(Visibility.PUBLIC)
                        .setFinal(true)
                        .setName("NODE_NAME")
                        .setType(String.class)
                        .setStringInitializer(javaClass.getCanonicalName())
                        .setStatic(true);

                StringBuilder indexedProperties=null;
                String indexName = null;
                for (Property prop : loopClass.properties()) {

                    //add helper name
                    javaClass.addField()
                            .setVisibility(Visibility.PUBLIC)
                            .setFinal(true)
                            .setName(prop.name().toUpperCase())
                            .setType(String.class)
                            .setStringInitializer(prop.name())
                            .setStatic(true);

                    if (prop instanceof Attribute) {
                        javaClass.addImport(Type.class);
                        FieldSource<JavaClassSource> typeHelper = javaClass.addField()
                                .setVisibility(Visibility.PUBLIC)
                                .setFinal(true)
                                .setName(prop.name().toUpperCase() + "_TYPE")
                                .setType(byte.class)
                                .setStatic(true);
                        switch (prop.type()) {
                            case "String":
                                typeHelper.setLiteralInitializer("Type.STRING");
                                break;
                            case "Double":
                                typeHelper.setLiteralInitializer("Type.DOUBLE");
                                break;
                            case "Long":
                                typeHelper.setLiteralInitializer("Type.LONG");
                                break;
                            case "Integer":
                                typeHelper.setLiteralInitializer("Type.INT");
                                break;
                            case "Boolean":
                                typeHelper.setLiteralInitializer("Type.BOOL");
                                break;
                            default:
                                throw new RuntimeException("Unknown type: " + prop.type() + ". Please update the generator.");
                        }
                    }

                    //POJO generation
                    if (!prop.derived() && !prop.learned()) {

                        if (prop instanceof Relation) {
                            //generate getter
                            String resultType = typeToClassName(prop.type());
                            MethodSource<JavaClassSource> getter = javaClass.addMethod();
                            getter.setVisibility(Visibility.PUBLIC);
                            getter.setFinal(true);
                            getter.setReturnTypeVoid();
                            getter.setName(toCamelCase("get " + prop.name()));
                            getter.addParameter("greycat.Callback<" + resultType + "[]>","callback");
                            getter.setBody(
                                   "this.relation(" + prop.name().toUpperCase() + ",new greycat.Callback<greycat.Node[]>() {\n" +
                                           "@Override\n" +
                                           "public void on(greycat.Node[] nodes) {\n" +
                                           resultType + "[] result = new " + resultType + "[nodes.length];\n" +
                                           "for(int i=0;i<result.length;i++) {\n" +
                                           "result[i] = (" + resultType + ") nodes[i];\n" +
                                           "}\n" +
                                           "callback.on(result);" +
                                           "}\n" +
                                           "});"
                            );



                            //generate setter
                            StringBuilder bodyBuilder = new StringBuilder();
                            MethodSource<JavaClassSource> add = javaClass.addMethod();
                            add.setVisibility(Visibility.PUBLIC).setFinal(true);
                            add.setName(toCamelCase("addTo " + prop.name()));
                            add.setReturnType(formatFqn(classifier));
                            add.addParameter(typeToClassName(prop.type()), "value");
                            bodyBuilder.append("super.addToRelation(").append(prop.name().toUpperCase()).append(",(greycat.Node)value);");
                            if(prop.parameters().get("opposite") != null) { //todo optimize
                                String methoName = prop.parameters().get("opposite");
                                bodyBuilder.append("value.internal_addTo")
                                        .append(methoName.substring(0,1).toUpperCase())
                                        .append(methoName.substring(1).toLowerCase())
                                        .append("(")
                                        .append("this")
                                        .append(");\n");
                            }
                            bodyBuilder.append("return this;");
                            add.setBody(bodyBuilder.toString());

                            bodyBuilder = null;
                            bodyBuilder = new StringBuilder();
                            //generate setter
                            MethodSource<JavaClassSource> remove = javaClass.addMethod();
                            remove.setVisibility(Visibility.PUBLIC).setFinal(true);
                            remove.setName(toCamelCase("removeFrom " + prop.name()));
                            remove.setReturnType(formatFqn(classifier));
                            remove.addParameter(typeToClassName(prop.type()), "value");
                            bodyBuilder.append("super.removeFromRelation(").append(prop.name().toUpperCase()).append(",(greycat.Node)value);");
                            if(prop.parameters().get("opposite") != null) { //todo optimize
                                String methoName = prop.parameters().get("opposite");
                                bodyBuilder.append("value.internal_removeFrom")
                                        .append(methoName.substring(0,1).toUpperCase())
                                        .append(methoName.substring(1).toLowerCase())
                                        .append("(")
                                        .append("this")
                                        .append(");\n");
                            }
                            bodyBuilder.append("return this;");
                            remove.setBody(bodyBuilder.toString());

                            //generate internal add and remove if needed
                            //todo must be optimize
                            if(prop.parameters().get("opposite") != null) {
                                MethodSource<JavaClassSource> internalRemove = javaClass.addMethod();
                                internalRemove.setVisibility(Visibility.PACKAGE_PRIVATE);
                                internalRemove.setName(toCamelCase("internal_removeFrom " + prop.name()));
                                internalRemove.setReturnTypeVoid();
                                internalRemove.addParameter(typeToClassName(prop.type()),"value");
                                internalRemove.setBody("super.removeFromRelation(" + prop.name().toUpperCase() + ",(greycat.Node)value);");

                                MethodSource<JavaClassSource> internalAdd = javaClass.addMethod();
                                internalAdd.setVisibility(Visibility.PACKAGE_PRIVATE);
                                internalAdd.setName(toCamelCase("internal_addTo " + prop.name()));
                                internalAdd.setReturnTypeVoid();
                                internalAdd.addParameter(typeToClassName(prop.type()),"value");
                                internalAdd.setBody("super.addToRelation(" + prop.name().toUpperCase() + ",(greycat.Node)value);");
                            }

                        } else {

                            if (prop.algorithm() != null) {
                                useML = true;
                                //attribute will be processed as a sub node
                                //generate getter
                                MethodSource<JavaClassSource> getter = javaClass.addMethod();
                                getter.setVisibility(Visibility.PUBLIC).setFinal(true);
                                getter.setReturnType(typeToClassName(prop.type()));
                                getter.setName(toCamelCase("get " + prop.name()));

                                getter.setBody("\t\tfinal DeferCounterSync waiter = this.graph().newSyncCounter(1);\n" +
                                        "this.relation(" + prop.name().toUpperCase() + ", new greycat.Callback<greycat.Node[]>() {\n" +
                                        "@Override\n" +
                                        "public void on(greycat.Node[] raw) {\n" +
                                        "if (raw == null || raw.length == 0) {\n" +
                                        "waiter.count();\n" +
                                        "} else {\n" +
                                        "RegressionNode casted = (RegressionNode) raw[0];\n" +
                                        "casted.extrapolate(waiter.wrap());\n" +
                                        "}\n" +
                                        "}\n" +
                                        "});\n" +
                                        "return (" + typeToClassName(prop.type()) + ") waiter.waitResult();");

                                //generate setter
                                MethodSource<JavaClassSource> setter = javaClass.addMethod();
                                setter.setVisibility(Visibility.PUBLIC).setFinal(true);
                                setter.setName(toCamelCase("set " + prop.name()));
                                setter.setReturnType(formatFqn(classifier));
                                setter.addParameter(typeToClassName(prop.type()), "value");

                                StringBuffer buffer = new StringBuffer();
                                buffer.append(" final DeferCounterSync waiter = this.graph().newSyncCounter(1);\n" +
                                        "        final " + formatFqn(classifier) + " selfPointer = this;\n" +
                                        "        this.relation(" + prop.name().toUpperCase() + ", new greycat.Callback<greycat.Node[]>() {\n" +
                                        "            @Override\n" +
                                        "            public void on(greycat.Node[] raw) {\n" +
                                        "                if (raw == null || raw.length == 0) {\n" +
                                        "                    RegressionNode casted = (RegressionNode) graph().newTypedNode(world(),time(),\"" + prop.algorithm() + "\");\n" +
                                        "                    selfPointer.addToRelation(" + prop.name().toUpperCase() + ",casted);\n");

                                for (String key : prop.parameters().keySet()) {
                                    buffer.append("casted.set(\"" + key + "\"," + prop.parameters().get(key) + ");\n");
                                }

                                buffer.append("                 casted.learn(value, waiter.wrap());\n" +
                                        "                } else {\n" +
                                        "                    RegressionNode casted = (RegressionNode) raw[0];\n" +
                                        "                    casted.learn(value, waiter.wrap());\n" +
                                        "                }\n" +
                                        "            }\n" +
                                        "        });\n" +
                                        "        waiter.waitResult();\n" +
                                        "        return this;");

                                setter.setBody(buffer.toString());
                            } else {

                                //generate getter
                                MethodSource<JavaClassSource> getter = javaClass.addMethod();
                                getter.setVisibility(Visibility.PUBLIC).setFinal(true);
                                getter.setReturnType(typeToClassName(prop.type()));
                                getter.setName(toCamelCase("get " + prop.name()));
                                getter.setBody("return (" + typeToClassName(prop.type()) + ") super.get(" + prop.name().toUpperCase() + ");");


                                //generate setter
                                javaClass.addMethod()
                                        .setVisibility(Visibility.PUBLIC).setFinal(true)
                                        .setName(toCamelCase("set " + prop.name()))
                                        .setReturnType(formatFqn(classifier))
                                        .setBody("super.set(" + prop.name().toUpperCase() + ", " + prop.name().toUpperCase()
                                            + "_TYPE,value);\nreturn this;"
                                        )
                                        .addParameter(typeToClassName(prop.type()), "value");

                                if(prop.indexes().length > 0) {
                                    if(indexedProperties == null) {
                                        indexedProperties = new StringBuilder();
                                        indexName = prop.indexes()[0].fqn().toUpperCase();
                                    } else {
                                        indexedProperties.append(",");
                                    }

                                    indexedProperties.append(prop.name().toUpperCase());

                                }

                            }

                        }

                    }
                }

                if(indexedProperties != null) {
                    javaClass.addMethod()
                            .setName("index" + classifier.name())
                            .setVisibility(Visibility.PUBLIC)
                            .setFinal(true)
                            .setReturnTypeVoid()
                            .setBody("final greycat.DeferCounterSync waiter = this.graph().newSyncCounter(1);\n" +
                                    "\t\tfinal " + classifier.name() +" self = this;\n" +
                                    "\t\tthis.graph().index(world(), time(), " + name + "Model.IDX_" + indexName + ", new greycat.Callback<greycat.NodeIndex>() {\n" +
                                    "\t\t\t@Override\n" +
                                    "\t\t\tpublic void on(greycat.NodeIndex indexNode) {\n" +
                                    "\t\t\t\tindexNode.removeFromIndex(self, " + indexedProperties +" );\n" +
                                    "\t\t\t\tindexNode.addToIndex(self," + indexedProperties +");\n" +
                                    "\t\t\t\twaiter.count();\n" +
                                    "\t\t\t}\n" +
                                    "\t\t});\n" +
                                    "\t\twaiter.waitResult();");
                }

                sources.add(javaClass);

            }
        }


        //Generate plugin
        final JavaClassSource pluginClass = Roaster.create(JavaClassSource.class);
        pluginClass.addImport(NodeFactory.class);
        pluginClass.addImport(Graph.class);
        if (name.contains(".")) {
            pluginClass.setPackage(name.substring(0, name.lastIndexOf('.')).toLowerCase());
            pluginClass.setName(name.substring(name.lastIndexOf('.') + 1) + "Plugin");
        } else {
            pluginClass.setName(name + "Plugin");
        }
        pluginClass.addInterface("greycat.plugin.Plugin");

        pluginClass.addMethod().setReturnTypeVoid()
                .setVisibility(Visibility.PUBLIC)
                .setName("stop")
                .setBody("")
                .addAnnotation(Override.class);

        StringBuilder startBodyBuilder = new StringBuilder();
        for (Classifier classifier : model.classifiers()) {
            if (classifier instanceof Class) {
                String fqn = formatFqn(classifier);
                startBodyBuilder.append("\t\tgraph.nodeRegistry()\n")
                        .append("\t\t\t.getOrCreateDeclaration(").append(fqn).append(".NODE_NAME").append(")").append("\n")
                        .append("\t\t\t.setFactory(new NodeFactory() {\n" +
                                "\t\t\t\t\t@Override\n" +
                                "\t\t\t\t\tpublic greycat.Node create(long world, long time, long id, Graph graph) {\n" +
                                "\t\t\t\t\t\treturn new ").append(fqn).append("(world,time,id,graph);\n" +
                                "\t\t\t\t\t}\n" +
                                "\t\t\t\t});\n");

            }
        }

        MethodSource<JavaClassSource> startMethod = pluginClass.addMethod();
        startMethod.setReturnTypeVoid()
                .setVisibility(Visibility.PUBLIC)
                .addAnnotation(Override.class);
        startMethod.setBody(startBodyBuilder.toString());
        startMethod.setName("start");
        startMethod.addParameter("Graph","graph");

        sources.add(pluginClass);

        //Generate model
        final JavaClassSource modelClass = Roaster.create(JavaClassSource.class);
        if (name.contains(".")) {
            modelClass.setPackage(name.substring(0, name.lastIndexOf('.')).toLowerCase());
            modelClass.setName(name.substring(name.lastIndexOf('.') + 1) + "Model");
        } else {
            modelClass.setName(name + "Model");
        }
        modelClass.addField().setName("_graph").setVisibility(Visibility.PRIVATE).setType(Graph.class).setFinal(true);

        modelClass.addField().setName("PLANNED_WORLD").setVisibility(Visibility.PUBLIC).setType("long").setStatic(true).setLiteralInitializer("greycat.Constants.NULL_LONG");
        modelClass.addField().setName("REAL_WORLD").setVisibility(Visibility.PUBLIC).setType("long").setStatic(true).setFinal(true).setLiteralInitializer("0");

        //add indexes name
        for (Classifier classifier : model.classifiers()) {
            if (classifier instanceof Index) {
                Index index = (Index) classifier;
                modelClass.addField()
                        .setVisibility(Visibility.PUBLIC)
                        .setStatic(true)
                        .setFinal(true)
                        .setName("IDX_" + index.name().toUpperCase())
                        .setType(String.class)
                        .setStringInitializer(index.name());
            }
        }

        MethodSource<JavaClassSource> modelConstructor = modelClass.addMethod().setConstructor(true).setVisibility(Visibility.PUBLIC);
        modelConstructor.addParameter(GraphBuilder.class, "builder");
        if (useML) {
            modelConstructor.setBody("this._graph = builder.withPlugin(new MLPlugin()).withPlugin(new " + name + "Plugin()).build();");
        } else {
            modelConstructor.setBody("this._graph = builder.withPlugin(new " + name + "Plugin()).build();");
        }
        modelClass.addMethod().setName("graph").setBody("return this._graph;").setVisibility(Visibility.PUBLIC).setFinal(true).setReturnType(Graph.class);

        //NOW method
        modelClass.addMethod()
                .setName("NOW")
                .setBody("return System.currentTimeMillis();")
                .setVisibility(Visibility.PUBLIC)
                .setFinal(true)
                .setStatic(true)
                .setReturnType("long");

        //Connect method
        modelClass.addImport(Callback.class);
        modelClass
                .addMethod()
                .setName("connect")
                .setBody("_graph.connect(new greycat.Callback<Boolean>() {\n" +
                        "\t\t\t@Override\n" +
                        "\t\t\tpublic void on(Boolean result) {\n" +
                        "\t\t\t\tif(PLANNED_WORLD == greycat.Constants.NULL_LONG) {\n" +
                        "\t\t\t\t    PLANNED_WORLD = graph().fork(REAL_WORLD);\n" +
                        "                }\n" +
                        "                callback.on(result);\n" +
                        "\t\t\t}\n" +
                        "\t\t});")
                .setVisibility(Visibility.PUBLIC)
                .setFinal(true)
                .setReturnTypeVoid()
                .addParameter("greycat.Callback<Boolean>", "callback");

        //Diconnect method
        modelClass
                .addMethod()
                .setName("disconnect")
                .setBody("_graph.disconnect(callback);")
                .setVisibility(Visibility.PUBLIC)
                .setFinal(true)
                .setReturnTypeVoid()
                .addParameter("greycat.Callback<Boolean>", "callback");

        //save method
        modelClass
                .addMethod()
                .setName("save")
                .setBody("_graph.save(callback);")
                .setVisibility(Visibility.PUBLIC)
                .setFinal(true)
                .setReturnTypeVoid()
                .addParameter("greycat.Callback<Boolean>", "callback");


        for (Classifier classifier : model.classifiers()) {
            if (classifier instanceof Class) {
                MethodSource<JavaClassSource> loopNewMethod = modelClass.addMethod().setName(toCamelCase("new " + classifier.name()));
                loopNewMethod.setVisibility(Visibility.PUBLIC).setFinal(true);
                loopNewMethod.setReturnType(formatFqn(classifier));
                loopNewMethod.addParameter("long", "world");
                loopNewMethod.addParameter("long", "time");
                loopNewMethod.setBody("return (" + formatFqn(classifier) + ")this._graph.newTypedNode(world,time," + formatFqn(classifier) + ".NODE_NAME);");
            }
            if (classifier instanceof Index) {
                Index casted = (Index) classifier;
                String resultType = formatFqn(casted.type());

                MethodSource<JavaClassSource> loopFindMethod = modelClass.addMethod().setName(toCamelCase("find " + classifier.name()));
                loopFindMethod.setVisibility(Visibility.PUBLIC).setFinal(true);
                loopFindMethod.setReturnTypeVoid();
                loopFindMethod.addParameter("long", "world");
                loopFindMethod.addParameter("long", "time");
                loopFindMethod.addParameter("String", "query");
                loopFindMethod.addParameter("greycat.Callback<" + resultType + "[]>","callback");
                loopFindMethod.setBody(
                        "       this._graph.indexIfExists(world, time, IDX_" + casted.fqn().toUpperCase() + ", new greycat.Callback<greycat.NodeIndex>() {\n" +
                        "           @Override\n" +
                        "           public void on(greycat.NodeIndex index) {\n" +
                        "               if(index != null) {\n" +
                        "                   index.find(new greycat.Callback<greycat.Node[]>() {\n" +
                        "                       @Override\n" +
                        "                       public void on(greycat.Node[] nodes) {\n" +
                        "                           " + resultType + "[] result = new " + resultType + "[nodes.length];\n" +
                        "                           for (int i = 0; i < result.length; i++) {\n" +
                        "                               result[i] = (" + resultType + ") nodes[i];\n" +
                        "                           }\n" +
                        "                           callback.on(result);\n" +
                        "                       }\n" +
                        "                   },query);\n" +
                        "               }\n" +
                        "           }\n" +
                        "       });"
                );

                MethodSource<JavaClassSource> loopFindAllMethod = modelClass.addMethod().setName(toCamelCase("findAll " + classifier.name()));
                loopFindAllMethod.setVisibility(Visibility.PUBLIC).setFinal(true);
                loopFindAllMethod.setReturnTypeVoid();
                loopFindAllMethod.addParameter("long", "world");
                loopFindAllMethod.addParameter("long", "time");
                loopFindAllMethod.addParameter("greycat.Callback<" + resultType + "[]>","callback");
                loopFindAllMethod.setBody(
                        "       this._graph.indexIfExists(world, time, IDX_" + casted.fqn().toUpperCase() + ", new greycat.Callback<greycat.NodeIndex>() {\n" +
                                "           @Override\n" +
                                "           public void on(greycat.NodeIndex index) {\n" +
                                "               if(index != null) {\n" +
                                "                   index.find(new greycat.Callback<greycat.Node[]>() {\n" +
                                "                       @Override\n" +
                                "                       public void on(greycat.Node[] nodes) {\n" +
                                "                           " + resultType + "[] result = new " + resultType + "[nodes.length];\n" +
                                "                           for (int i = 0; i < result.length; i++) {\n" +
                                "                               result[i] = (" + resultType + ") nodes[i];\n" +
                                "                           }\n" +
                                "                           callback.on(result);\n" +
                                "                       }\n" +
                                "                   });\n" +
                                "               }\n" +
                                "           }\n" +
                                "       });"
                );
            }
        }


        sources.add(modelClass);


        // Generate Task API
        final JavaClassSource taskAPI = Roaster.create(JavaClassSource.class);
        if(name.contains(".")) {
            taskAPI.setPackage(name.substring(0, name.lastIndexOf('.')).toLowerCase() + ".task");
            taskAPI.setName(name.substring(name.lastIndexOf('.') + 1) + "TaskAPI");
        } else {
            taskAPI.setPackage("task");
            taskAPI.setName(name + "TaskAPI");
        }

        taskAPI.addMethod()
                .setName("travelInRealWorld")
                .setBody("return greycat.internal.task.CoreActions.travelInWorld(" + name + "Model.REAL_WORLD + \"\");")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setReturnType("greycat.Action");

        taskAPI.addMethod()
                .setName("travelInPlannedWorld")
                .setBody("return greycat.internal.task.CoreActions.travelInWorld(" + name + "Model.PLANNED_WORLD + \"\");")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setReturnType("greycat.Action");


        for(Classifier classifier: model.classifiers()) {
            if(classifier instanceof Class) {
                taskAPI.addMethod()
                        .setName("create" + classifier.name() + "Node")
                        .setReturnType("greycat.Action")
                        .setVisibility(Visibility.PUBLIC)
                        .setStatic(true)
                        .setBody("return greycat.internal.task.CoreActions.createTypedNode("+ formatFqn(classifier) + ".NODE_NAME);");

                for(Property property : ((Class) classifier).properties()) {
                    if(property instanceof Attribute) {
                        taskAPI.addMethod()
                                .setName("set" + classifier.name() + property.name().substring(0,1).toUpperCase() + property.name().substring(1))
                                .setReturnType("greycat.Action")
                                .setVisibility(Visibility.PUBLIC)
                                .setStatic(true)
                                .setBody("return greycat.internal.task.CoreActions.setAttribute("+ formatFqn(classifier) + "." + property.name().toUpperCase()+"," + formatFqn(classifier) +"." + property.name().toUpperCase() + "_TYPE," + property.name() + " + \"\");")
                                .addParameter(String.class,property.name());

                        taskAPI.addMethod()
                                .setName("get" + classifier.name() + property.name().substring(0,1).toUpperCase() + property.name().substring(1))
                                .setReturnType("greycat.Action")
                                .setVisibility(Visibility.PUBLIC)
                                .setStatic(true)
                                .setBody("return greycat.internal.task.CoreActions.attribute(" + formatFqn(classifier) + "." + property.name().toUpperCase() +");");
                    } else if(property instanceof Relation) {
                        taskAPI.addMethod()
                                .setName("addTo" + classifier.name() + property.name().substring(0,1).toUpperCase() + property.name().substring(1))
                                .setReturnType("greycat.Action")
                                .setVisibility(Visibility.PUBLIC)
                                .setStatic(true)
                                .setBody("return greycat.internal.task.CoreActions.addVarToRelation(" + formatFqn(classifier) +"." + property.name().toUpperCase() + ",varName);")
                                .addParameter("String","varName");

                        taskAPI.addMethod()
                                .setName("traverse" + classifier.name() + property.name().substring(0,1).toUpperCase() + property.name().substring(1))
                                .setReturnType("greycat.Action")
                                .setVisibility(Visibility.PUBLIC)
                                .setStatic(true)
                                .setBody("return greycat.internal.task.CoreActions.traverse(" + formatFqn(classifier) +"." + property.name().toUpperCase() + ");");
                    }
                }



                // Create TaskFunctionSelect
                final JavaInterfaceSource functionSelect = Roaster.create(JavaInterfaceSource.class);
                functionSelect.setPackage(taskAPI.getPackage());
                functionSelect.setName("TaskFunctionSelect" + classifier.name());
                functionSelect.addAnnotation(FunctionalInterface.class);
                functionSelect.addInterface(TaskFunctionSelect.class);

                MethodSource first = functionSelect.addMethod()
                        .setName("select")
                        .setReturnType(boolean.class)
                        .setBody("return select((" + formatFqn(classifier) + ")node,ctx);")
                        .setDefault(true);
                first.addParameter("greycat.Node","node");
                first.addParameter("greycat.TaskContext","ctx");
                first.addAnnotation(Override.class);

                MethodSource second = functionSelect.addMethod()
                        .setName("select")
                        .setReturnType(boolean.class);
                second.addParameter(formatFqn(classifier),classifier.name().toLowerCase());
                second.addParameter("greycat.TaskContext","ctx");



                sources.add(functionSelect);





            } else if(classifier instanceof Index) {
                taskAPI.addMethod()
                        .setName("findAll" + ((Index)classifier).type().name() + "s")
                        .setReturnType("greycat.Action")
                        .setVisibility(Visibility.PUBLIC)
                        .setStatic(true)
                        .setBody("return greycat.internal.task.CoreActions.readGlobalIndex(" + name +"Model.IDX_" + classifier.name().toUpperCase() +");");

                StringBuilder indexedProperties = new StringBuilder();
                Property[] properties = ((Index)classifier).properties();
                for(int iIdxProp = 0; iIdxProp<properties.length; iIdxProp++) {
                    indexedProperties.append(formatFqn(((Index) classifier).type()))
                            .append(".")
                            .append(properties[iIdxProp].name().toUpperCase());

                    if(iIdxProp < properties.length - 1) {
                        indexedProperties.append(",");
                    }
                }
                taskAPI.addMethod()
                        .setName("index" + ((Index)classifier).type().name())
                        .setReturnType("greycat.Action")
                        .setVisibility(Visibility.PUBLIC)
                        .setStatic(true)
                        .setBody("return greycat.internal.task.CoreActions.addToGlobalIndex(" + name+ "Model.IDX_" + classifier.name().toUpperCase()+", " + indexedProperties + ");");

            }
        }

       taskAPI.addMethod()
                .setName("getPlannedVersionNode")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(Action.class)
                .setBody("return new ActionResolvedInOtherWorld(true);");

        taskAPI.addMethod()
                .setName("getRealVersionNode")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(Action.class)
                .setBody("return new ActionResolvedInOtherWorld(false);");

        sources.add(taskAPI);

        // Generate ActionResolvedInOtherWorld
        final JavaClassSource actionResolvedPW = Roaster.create(JavaClassSource.class);
        if(name.contains(".")) {
            actionResolvedPW.setPackage(name.substring(0, name.lastIndexOf('.')).toLowerCase() + ".task");
            actionResolvedPW.setName("ActionResolvedInOtherWorld");
        } else {
            actionResolvedPW.setPackage("task");
            actionResolvedPW.setName("ActionResolvedInOtherWorld");
        }

        actionResolvedPW.addField()
                .setName("getPlanned")
                .setVisibility(Visibility.PRIVATE)
                .setFinal(true)
                .setType(boolean.class);

        actionResolvedPW.addMethod()
                .setConstructor(true)
                .setVisibility(Visibility.PUBLIC)
                .setBody("this.getPlanned = p_getPlanned;")
                .addParameter(boolean.class,"p_getPlanned");

        actionResolvedPW.addImport(Action.class);
        actionResolvedPW.addImport(Callback.class);
        actionResolvedPW.addImport(DeferCounter.class);
        actionResolvedPW.addImport(Node.class);
        actionResolvedPW.addImport(TaskContext.class);
        actionResolvedPW.addImport(TaskResult.class);
        actionResolvedPW.addImport(BaseNode.class);
        actionResolvedPW.addImport(CoreDeferCounter.class);
        actionResolvedPW.addImport(Job.class);
        actionResolvedPW.addImport(Buffer.class);

        actionResolvedPW.addInterface(Action.class);

        MethodSource<JavaClassSource> evalMethod = actionResolvedPW.addMethod()
                .setName("eval")
                .setVisibility(Visibility.PUBLIC)
                .setBody("final TaskResult previous = ctx.result();\n" +
                        "        final TaskResult newRes = ctx.newResult();\n" +
                        "        final long[] ids = new long[previous.size()];\n" +
                        "        int nbElements = 0;\n" +
                        "\n" +
                        "        for(int i=0;i<previous.size();i++) {\n" +
                        "            if(previous.get(i) instanceof BaseNode) {\n" +
                        "                nbElements++;\n" +
                        "                ids[i] = ((BaseNode) previous.get(i)).id();\n" +
                        "            }\n" +
                        "        }\n" +
                        "\n" +
                        "        final DeferCounter counter = new CoreDeferCounter(nbElements);\n" +
                        "        final long world = (getPlanned)? " + name + "Model.PLANNED_WORLD : "+ name + "Model.REAL_WORLD;\n" +
                        "        for(int i=0;i<nbElements;i++) {\n" +
                        "            ctx.graph().lookup(world, ctx.time(), ids[i], new Callback<Node>() {\n" +
                        "                @Override\n" +
                        "                public void on(Node result) {\n" +
                        "                    newRes.add(result);\n" +
                        "                    counter.count();\n" +
                        "                }\n" +
                        "            });\n" +
                        "        }\n" +
                        "\n" +
                        "        counter.then(new Job() {\n" +
                        "            @Override\n" +
                        "            public void run() {\n" +
                        "                ctx.continueWith(newRes);\n" +
                        "            }\n" +
                        "        });");
        evalMethod.addParameter(TaskContext.class,"ctx");
        evalMethod.addAnnotation(Override.class);

        MethodSource<JavaClassSource> serializeMethod = actionResolvedPW.addMethod()
                .setName("serialize")
                .setVisibility(Visibility.PUBLIC)
                .setBody("//todo");
        serializeMethod.addParameter(Buffer.class,"builder");
        serializeMethod.addAnnotation(Override.class);

        sources.add(actionResolvedPW);

        // Generate Time Helper
        final JavaClassSource timeHelper = Roaster.create(JavaClassSource.class);
        if(name.contains(".")) {
            timeHelper.setPackage(name.substring(0, name.lastIndexOf('.')).toLowerCase() + ".time");
            timeHelper.setName(name.substring(name.lastIndexOf('.') + 1) + "TimeHelper");
        } else {
            timeHelper.setPackage("time");
            timeHelper.setName(name + "TimeHelper");
        }


        timeHelper.addField()
                .setName("ONE_SECOND")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setType(long.class)
                .setLiteralInitializer("1000");
        timeHelper.addField()
                .setName("ONE_MINUTE")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setType(long.class)
                .setLiteralInitializer("60 * ONE_SECOND");
        timeHelper.addField()
                .setName("ONE_HOUR")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setType(long.class)
                .setLiteralInitializer("60 * ONE_MINUTE");
        timeHelper.addField()
                .setName("ONE_DAY")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setType(long.class)
                .setLiteralInitializer("24 * ONE_HOUR");


        timeHelper.addMethod()
                .setName("seconds")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(long.class)
                .setBody("return nb * ONE_SECOND;")
                .addParameter(int.class,"nb");
        timeHelper.addMethod()
                .setName("minutes")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(long.class)
                .setBody("return nb * ONE_MINUTE;")
                .addParameter(int.class,"nb");
        timeHelper.addMethod()
                .setName("hours")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(long.class)
                .setBody("return nb * ONE_HOUR;")
                .addParameter(int.class,"nb");
        timeHelper.addMethod()
                .setName("days")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(long.class)
                .setBody("return nb * ONE_DAY;")
                .addParameter(int.class,"nb");

        MethodSource nextSeconds = timeHelper.addMethod()
                .setName("nextSeconds")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(long.class)
                .setBody("return current + seconds(nb);");
        nextSeconds.addParameter(long.class,"current");
        nextSeconds.addParameter(int.class,"nb");

        MethodSource prevSeconds = timeHelper.addMethod()
                .setName("prevSeconds")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(long.class)
                .setBody("return current - seconds(nb);");
        prevSeconds.addParameter(long.class,"current");
        prevSeconds.addParameter(int.class,"nb");

        MethodSource nextMinutes = timeHelper.addMethod()
                .setName("nextMinutes")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(long.class)
                .setBody("return current + minutes(nb);");
        nextMinutes.addParameter(long.class,"current");
        nextMinutes.addParameter(int.class,"nb");

        MethodSource prevMinutes = timeHelper.addMethod()
                .setName("prevMinutes")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(long.class)
                .setBody("return current - minutes(nb);");
        prevMinutes.addParameter(long.class,"current");
        prevMinutes.addParameter(int.class,"nb");

        MethodSource nextHours = timeHelper.addMethod()
                .setName("nextHours")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(long.class)
                .setBody("return current + hours(nb);");
        nextHours.addParameter(long.class,"current");
        nextHours.addParameter(int.class,"nb");

        MethodSource prevHours = timeHelper.addMethod()
                .setName("prevHours")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(long.class)
                .setBody("return current - hours(nb);");
        prevHours.addParameter(long.class,"current");
        prevHours.addParameter(int.class,"nb");

        MethodSource nextDays = timeHelper.addMethod()
                .setName("nextDays")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(long.class)
                .setBody("return current + days(nb);");
        nextDays.addParameter(long.class,"current");
        nextDays.addParameter(int.class,"nb");

        MethodSource prevDays = timeHelper.addMethod()
                .setName("prevDays")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true)
                .setFinal(true)
                .setReturnType(long.class)
                .setBody("return current - days(nb);");
        prevDays.addParameter(long.class,"current");
        prevDays.addParameter(int.class,"nb");

        sources.add(timeHelper);




        //DEBUG print
        for (JavaSource src : sources) {

            File targetPkg;
            if (src.getPackage() != null) {
                targetPkg = new File(target.getAbsolutePath() + File.separator + src.getPackage().replace(".", File.separator));
            } else {
                targetPkg = target;
            }
            targetPkg.mkdirs();
            File targetSrc = new File(targetPkg, src.getName() + ".java");
            try {
                FileWriter writer = new FileWriter(targetSrc);
                writer.write(src.toString());
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private String toCamelCase(final String init) {
        if (init == null) {
            return null;
        }
        final StringBuilder ret = new StringBuilder(init.length());
        boolean isFirst = true;
        for (final String word : init.split(" ")) {
            if (isFirst) {
                ret.append(word);
                isFirst = false;
            } else {
                if (!word.isEmpty()) {
                    ret.append(word.substring(0, 1).toUpperCase());
                    ret.append(word.substring(1).toLowerCase());
                }
            }
        }
        return ret.toString();
    }

    private static byte nameToType(final String name) {
        switch (name) {
            case "Integer":
                return Type.INT;
            case "Long":
                return Type.LONG;
            case "String":
                return Type.STRING;
            case "Double":
                return Type.DOUBLE;
        }
        return -1;
    }

    private static String typeToClassName(String mwgTypeName) {
        byte mwgType = nameToType(mwgTypeName);
        switch (mwgType) {
            case Type.BOOL:
                return Boolean.class.getCanonicalName();
            case Type.DOUBLE:
                return Double.class.getCanonicalName();
            case Type.INT:
                return Integer.class.getCanonicalName();
            case Type.LONG:
                return Long.class.getCanonicalName();
            case Type.STRING:
                return String.class.getCanonicalName();
        }
        return formatTypeString(mwgTypeName);
    }

    private static String formatFqn(Classifier classifier) {
        if(classifier.pack() != null) {
            return classifier.pack().toLowerCase() + "." + classifier.name();
        }
        return classifier.name();
    }

    private static String formatTypeString(String type) {
        int lastPoint = type.lastIndexOf(".");
        return type.substring(0,lastPoint).toLowerCase() + type.substring(lastPoint);
    }


}
