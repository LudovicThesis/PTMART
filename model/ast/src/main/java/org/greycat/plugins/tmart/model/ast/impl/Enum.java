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

import org.greycat.plugins.tmart.model.ast.KEnum;

import java.util.Set;
import java.util.TreeSet;

public class Enum implements KEnum {

    private final Set<String> literals;

    private final String pack;

    private final String name;

    public Enum(String fqn) {
        if (fqn.contains(".")) {
            name = fqn.substring(fqn.lastIndexOf('.')+1);
            pack = fqn.substring(0, fqn.lastIndexOf('.'));
        } else {
            name = fqn;
            pack = null;
        }
        literals = new TreeSet<String>();
    }

    @Override
    public String[] literals() {
        return literals.toArray(new String[literals.size()]);
    }

    @Override
    public void addLiteral(String value) {
        literals.add(value);
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
