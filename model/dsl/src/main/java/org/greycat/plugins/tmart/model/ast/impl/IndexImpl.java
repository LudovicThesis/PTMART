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
package org.greycat.plugins.tmart.model.ast.impl;

import org.greycat.plugins.tmart.model.ast.Class;
import org.greycat.plugins.tmart.model.ast.Property;

import java.util.Set;
import java.util.TreeSet;

public class Index implements org.greycat.plugins.tmart.model.ast.Index {

    private final Set<Property> literals;

    private final String pack;

    private final String name;

    private final Class clazz;

    public Index(String fqn, Class clazz) {
        this.clazz = clazz;
        if (fqn.contains(".")) {
            name = fqn.substring(fqn.lastIndexOf('.') + 1);
            pack = fqn.substring(0, fqn.lastIndexOf('.'));
        } else {
            name = fqn;
            pack = null;
        }
        literals = new TreeSet<Property>();
    }

    @Override
    public Property[] properties() {
        return literals.toArray(new Property[literals.size()]);
    }

    @Override
    public void addProperty(String value) {
        Property prop = clazz.property(value);
        literals.add(prop);
        prop.addIndex(this);
    }

    @Override
    public Class type() {
        return this.clazz;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String fqn() {
        if (pack != null) {
            return pack + "." + name;
        } else {
            return name;
        }
    }

    @Override
    public String pack() {
        return pack;
    }

}
