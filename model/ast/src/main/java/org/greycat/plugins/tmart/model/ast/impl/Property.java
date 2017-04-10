/**
 * Copyright 2017 The GreyCat Authors.  All rights reserved.
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
package org.kevoree.modeling.ast.impl;

import org.greycat.plugins.tmart.model.ast.KDependency;
import org.greycat.plugins.tmart.model.ast.KIndex;
import org.greycat.plugins.tmart.model.ast.KProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Property implements KProperty {

    private final String name;

    private final String type;

    private final List<KDependency> dependencies;

    private final List<KIndex> indexes;

    private final Map<String, String> paramaters;

    private String alg;

    private boolean derived = false;

    private boolean learned = false;

    private boolean global = false;

    public Property(String name, String type) {
        this.name = name;
        this.type = type;
        indexes = new ArrayList<KIndex>();
        dependencies = new ArrayList<KDependency>();
        paramaters = new HashMap<String, String>();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String algorithm() {
        return alg;
    }

    @Override
    public void setAlgorithm(String alg) {
        this.alg = alg;
    }

    @Override
    public KDependency[] dependencies() {
        return dependencies.toArray(new KDependency[dependencies.size()]);
    }

    @Override
    public void addIndex(KIndex index) {
        indexes.add(index);
    }

    @Override
    public KIndex[] indexes() {
        return indexes.toArray(new KIndex[indexes.size()]);
    }

    @Override
    public void addDependency(KDependency dependency) {
        dependencies.add(dependency);
    }

    @Override
    public Map<String, String> parameters() {
        return paramaters;
    }

    @Override
    public void addParameter(String param, String value) {
        paramaters.put(param, value);
    }

    @Override
    public boolean derived() {
        return derived;
    }

    @Override
    public void setDerived() {
        derived = true;
    }

    @Override
    public boolean learned() {
        return learned;
    }

    @Override
    public void setLearned() {
        learned = true;
    }

    @Override
    public boolean global() {
        return global;
    }

    @Override
    public void setGlobal() {
        global = true;
    }

    @Override
    public int compareTo(Object o) {
        Property p2 = (Property) o;
        if(p2.name().equals(name)){
            return 0;
        } else {
            return name().compareTo(p2.name);
        }
    }
}
