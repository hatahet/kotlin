/*
 * Copyright 2010-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.codegen;

import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class FrameMap {
    private final TObjectIntHashMap<DeclarationDescriptor> myVarIndex = new TObjectIntHashMap<DeclarationDescriptor>();
    private final TObjectIntHashMap<DeclarationDescriptor> myVarSizes = new TObjectIntHashMap<DeclarationDescriptor>();
    private int myMaxIndex = 0;

    public void enter(DeclarationDescriptor descriptor, int size) {
        myVarIndex.put(descriptor, myMaxIndex);
        myMaxIndex += size;
        myVarSizes.put(descriptor, size);
    }

    public int leave(DeclarationDescriptor descriptor) {
        int size = myVarSizes.get(descriptor);
        myMaxIndex -= size;
        myVarSizes.remove(descriptor);
        return myVarIndex.remove(descriptor);
    }

    public int enterTemp() {
        return myMaxIndex++;
    }

    public int enterTemp(int size) {
        int result = myMaxIndex;
        myMaxIndex += size;
        return result;
    }

    public void leaveTemp() {
        myMaxIndex--;
    }

    public void leaveTemp(int size) {
        myMaxIndex -= size;
    }

    public int getIndex(DeclarationDescriptor descriptor) {
        return myVarIndex.contains(descriptor) ? myVarIndex.get(descriptor) : -1;
    }

    public Mark mark() {
        return new Mark(myMaxIndex);
    }

    public class Mark {
        private final int myIndex;

        public Mark(int index) {
            myIndex = index;
        }

        public void dropTo() {
            List<DeclarationDescriptor> descriptorsToDrop = new ArrayList<DeclarationDescriptor>();
            TObjectIntIterator<DeclarationDescriptor> iterator = myVarIndex.iterator();
            while (iterator.hasNext()) {
                iterator.advance();
                if (iterator.value() >= myIndex) {
                    descriptorsToDrop.add(iterator.key());
                }
            }
            for (DeclarationDescriptor declarationDescriptor : descriptorsToDrop) {
                myVarIndex.remove(declarationDescriptor);
                myVarSizes.remove(declarationDescriptor);
            }
            myMaxIndex = myIndex;
        }
    }

}
