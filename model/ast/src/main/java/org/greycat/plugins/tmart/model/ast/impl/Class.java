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

import org.greycat.plugins.tmart.model.ast.KClass;
import org.greycat.plugins.tmart.model.ast.KProperty;

import java.util.HashMap;
import java.util.Map;

public class Class implements KClass {

    private final String pack;

    private final String name;

    private final Map<String, KProperty> properties;

    private KClass parent;

    public Class(String fqn) {
        if (fqn.contains(".")) {
            name = fqn.substring(fqn.lastIndexOf('.') + 1);
            pack = fqn.substring(0, fqn.lastIndexOf('.'));
        } else {
            name = fqn;
            pack = null;
        }
        properties = new HashMap<String, KProperty>();
    }

    @Override
    public KProperty[] properties() {
        return properties.values().toArray(new KProperty[properties.size()]);
    }

    @Override
    public KProperty property(String name) {
        for (KProperty property : properties()) {
            if (property.name().equals(name)) {
                return property;
            }
        }
        return null;
    }

    @Override
    public void addProperty(KProperty property) {
        properties.put(property.name(), property);
    }

    @Override
    public KClass parent() {
        return parent;
    }

    @Override
    public void setParent(KClass parent) {
        this.parent = parent;
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
