package org.jetbrains.jet.lang.resolve;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.diagnostics.DiagnosticHolder;
import org.jetbrains.jet.lang.types.JetType;

import java.util.*;

import static org.jetbrains.jet.lang.diagnostics.Errors.REDECLARATION;

/**
 * @author abreslav
 */
public class WritableScopeImpl extends WritableScopeWithImports {

    private final Collection<DeclarationDescriptor> allDescriptors = Sets.newLinkedHashSet();
    private boolean allDescriptorsDone = false;

    @NotNull
    private final DeclarationDescriptor ownerDeclarationDescriptor;

    // FieldNames include "$"
    @Nullable
    private Map<String, PropertyDescriptor> propertyDescriptorsByFieldNames;

    @Nullable
    private Map<String, WritableFunctionGroup> functionGroups;
    @Nullable
    private Map<String, DeclarationDescriptor> variableClassOrNamespaceDescriptors;

    @Nullable
    private Map<String, List<DeclarationDescriptor>> labelsToDescriptors;
    @Nullable
    private JetType thisType;

    private List<VariableDescriptor> variableDescriptors;

    public WritableScopeImpl(@NotNull JetScope scope, @NotNull DeclarationDescriptor owner, @NotNull DiagnosticHolder diagnosticHolder) {
        super(scope, diagnosticHolder);
        this.ownerDeclarationDescriptor = owner;
    }

    @NotNull
    @Override
    public DeclarationDescriptor getContainingDeclaration() {
        return ownerDeclarationDescriptor;
    }

    @Override
    public DeclarationDescriptor getDeclarationDescriptorForUnqualifiedThis() {
        if (DescriptorUtils.definesItsOwnThis(ownerDeclarationDescriptor)) {
            return ownerDeclarationDescriptor;
        }
        return super.getDeclarationDescriptorForUnqualifiedThis();
    }

    @Override
    public void importScope(@NotNull JetScope imported) {
        super.importScope(imported);
    }

    @Override
    public void importClassifierAlias(@NotNull String importedClassifierName, @NotNull ClassifierDescriptor classifierDescriptor) {
        allDescriptors.add(classifierDescriptor);
        super.importClassifierAlias(importedClassifierName, classifierDescriptor);
    }

    @Override
    public Collection<DeclarationDescriptor> getAllDescriptors() {
        if (!allDescriptorsDone) {
            allDescriptorsDone = true;
            allDescriptors.addAll(getWorkerScope().getAllDescriptors());
            for (JetScope imported : getImports()) {
                allDescriptors.addAll(imported.getAllDescriptors());
            }
        }
        return allDescriptors;
    }

    @NotNull
    private Map<String, List<DeclarationDescriptor>> getLabelsToDescriptors() {
        if (labelsToDescriptors == null) {
            labelsToDescriptors = new HashMap<String, List<DeclarationDescriptor>>();
        }
        return labelsToDescriptors;
    }

    @NotNull
    @Override
    public Collection<DeclarationDescriptor> getDeclarationsByLabel(@NotNull String labelName) {
        Collection<DeclarationDescriptor> superResult = super.getDeclarationsByLabel(labelName);
        Map<String, List<DeclarationDescriptor>> labelsToDescriptors = getLabelsToDescriptors();
        List<DeclarationDescriptor> declarationDescriptors = labelsToDescriptors.get(labelName);
        if (declarationDescriptors == null) {
            return superResult;
        }
        if (superResult.isEmpty()) return declarationDescriptors;
        List<DeclarationDescriptor> result = new ArrayList<DeclarationDescriptor>(declarationDescriptors);
        result.addAll(superResult);
        return result;
    }

    @Override
    public void addLabeledDeclaration(@NotNull DeclarationDescriptor descriptor) {
        Map<String, List<DeclarationDescriptor>> labelsToDescriptors = getLabelsToDescriptors();
        String name = descriptor.getName();
        assert name != null;
        List<DeclarationDescriptor> declarationDescriptors = labelsToDescriptors.get(name);
        if (declarationDescriptors == null) {
            declarationDescriptors = new ArrayList<DeclarationDescriptor>();
            labelsToDescriptors.put(name, declarationDescriptors);
        }
        declarationDescriptors.add(descriptor);
    }

    @NotNull
    private Map<String, DeclarationDescriptor> getVariableClassOrNamespaceDescriptors() {
        if (variableClassOrNamespaceDescriptors == null) {
            variableClassOrNamespaceDescriptors = Maps.newHashMap();
        }
        return variableClassOrNamespaceDescriptors;
    }

    @NotNull
    private List<VariableDescriptor> getVariableDescriptors() {
        if (variableDescriptors == null) {
            variableDescriptors = Lists.newArrayList();
        }
        return variableDescriptors;
    }

    @Override
    public void addVariableDescriptor(@NotNull VariableDescriptor variableDescriptor) {
        Map<String, DeclarationDescriptor> variableClassOrNamespaceDescriptors = getVariableClassOrNamespaceDescriptors();
        DeclarationDescriptor existingDescriptor = variableClassOrNamespaceDescriptors.get(variableDescriptor.getName());
        if (existingDescriptor != null) {
            diagnosticHolder.report(REDECLARATION.on(existingDescriptor, variableDescriptor));
        }
        // TODO : Should this always happen?
        variableClassOrNamespaceDescriptors.put(variableDescriptor.getName(), variableDescriptor);
        allDescriptors.add(variableDescriptor);

        getVariableDescriptors().add(variableDescriptor);
    }

    @Override
    public VariableDescriptor getVariable(@NotNull String name) {
        Map<String, DeclarationDescriptor> variableClassOrNamespaceDescriptors = getVariableClassOrNamespaceDescriptors();
        DeclarationDescriptor descriptor = variableClassOrNamespaceDescriptors.get(name);
        if (descriptor instanceof VariableDescriptor) {
            return (VariableDescriptor) descriptor;
        }

        if (thisType != null) {
            VariableDescriptor variable = getThisType().getMemberScope().getVariable(name);
            if (variable != null) {
                return variable;
            }
        }

        VariableDescriptor variableDescriptor = getWorkerScope().getVariable(name);
        if (variableDescriptor != null) {
            return variableDescriptor;
        }
        return super.getVariable(name);
    }

    @NotNull
    private Map<String, WritableFunctionGroup> getFunctionGroups() {
        if (functionGroups == null) {
            functionGroups = new HashMap<String, WritableFunctionGroup>();
        }
        return functionGroups;
    }

    @Override
    public void addFunctionDescriptor(@NotNull FunctionDescriptor functionDescriptor) {
        String name = functionDescriptor.getName();
        Map<String, WritableFunctionGroup> functionGroups = getFunctionGroups();

        @Nullable
        WritableFunctionGroup functionGroup = functionGroups.get(name);
        if (functionGroup == null) {
            functionGroup = new WritableFunctionGroup(name);
            functionGroups.put(name, functionGroup);
        }
        functionGroup.addFunction(functionDescriptor);
        allDescriptors.add(functionDescriptor);
    }

    @Override
    @NotNull
    public FunctionGroup getFunctionGroup(@NotNull String name) {
        WritableFunctionGroup result = new WritableFunctionGroup(name);
        
        FunctionGroup functionGroup = getFunctionGroups().get(name);
        if (functionGroup != null) {
            result.addAllFunctions(functionGroup);
        }
        
        if (thisType != null) {
            result.addAllFunctions(getThisType().getMemberScope().getFunctionGroup(name));
        }

        result.addAllFunctions(getWorkerScope().getFunctionGroup(name));

        result.addAllFunctions(super.getFunctionGroup(name));

        return result;
    }

    @Override
    public void addTypeParameterDescriptor(@NotNull TypeParameterDescriptor typeParameterDescriptor) {
        String name = typeParameterDescriptor.getName();
        addClassifierAlias(name, typeParameterDescriptor);
    }

    @Override
    public void addClassifierDescriptor(@NotNull ClassifierDescriptor classDescriptor) {
        addClassifierAlias(classDescriptor.getName(), classDescriptor);
    }

    @Override
    public void addClassifierAlias(@NotNull String name, @NotNull ClassifierDescriptor classifierDescriptor) {
        Map<String, DeclarationDescriptor> variableClassOrNamespaceDescriptors = getVariableClassOrNamespaceDescriptors();
        DeclarationDescriptor originalDescriptor = variableClassOrNamespaceDescriptors.get(name);
        if (originalDescriptor != null) {
            diagnosticHolder.report(REDECLARATION.on(originalDescriptor, classifierDescriptor));
        }
        variableClassOrNamespaceDescriptors.put(name, classifierDescriptor);
        allDescriptors.add(classifierDescriptor);
    }

    @Override
    public ClassifierDescriptor getClassifier(@NotNull String name) {
        Map<String, DeclarationDescriptor> variableClassOrNamespaceDescriptors = getVariableClassOrNamespaceDescriptors();
        DeclarationDescriptor descriptor = variableClassOrNamespaceDescriptors.get(name);
        if (descriptor instanceof ClassifierDescriptor) return (ClassifierDescriptor) descriptor;

        ClassifierDescriptor classifierDescriptor = getWorkerScope().getClassifier(name);
        if (classifierDescriptor != null) return classifierDescriptor;

        return super.getClassifier(name);
    }

    @NotNull
    @Override
    public JetType getThisType() {
        if (thisType == null) {
            return super.getThisType();
        }
        return thisType;
    }

    @Override
    public void addNamespace(@NotNull NamespaceDescriptor namespaceDescriptor) {
        Map<String, DeclarationDescriptor> variableClassOrNamespaceDescriptors = getVariableClassOrNamespaceDescriptors();
        DeclarationDescriptor oldValue = variableClassOrNamespaceDescriptors.put(namespaceDescriptor.getName(), namespaceDescriptor);
        if (oldValue != null) {
            diagnosticHolder.report(REDECLARATION.on(oldValue, namespaceDescriptor));
        }
        allDescriptors.add(namespaceDescriptor);
    }

    @Override
    public NamespaceDescriptor getDeclaredNamespace(@NotNull String name) {
        Map<String, DeclarationDescriptor> variableClassOrNamespaceDescriptors = getVariableClassOrNamespaceDescriptors();
        DeclarationDescriptor namespaceDescriptor = variableClassOrNamespaceDescriptors.get(name);
        if (namespaceDescriptor instanceof NamespaceDescriptor) return (NamespaceDescriptor) namespaceDescriptor;
        return null;
    }

    @Override
    public NamespaceDescriptor getNamespace(@NotNull String name) {
        NamespaceDescriptor declaredNamespace = getDeclaredNamespace(name);
        if (declaredNamespace != null) return declaredNamespace;

        NamespaceDescriptor namespace = getWorkerScope().getNamespace(name);
        if (namespace != null) return namespace;
        return super.getNamespace(name);
    }

    @Override
    public void setThisType(@NotNull JetType thisType) {
        if (this.thisType != null) {
            throw new UnsupportedOperationException("Receiver redeclared");
        }
        this.thisType = thisType;
    }

    @SuppressWarnings({"NullableProblems"})
    @NotNull
    private Map<String, PropertyDescriptor> getPropertyDescriptorsByFieldNames() {
        if (propertyDescriptorsByFieldNames == null) {
            propertyDescriptorsByFieldNames = new HashMap<String, PropertyDescriptor>();
        }
        return propertyDescriptorsByFieldNames;
    }

    @Override
    public void addPropertyDescriptorByFieldName(@NotNull String fieldName, @NotNull PropertyDescriptor propertyDescriptor) {
        getPropertyDescriptorsByFieldNames().put(fieldName, propertyDescriptor);
    }

    @Override
    public PropertyDescriptor getPropertyByFieldReference(@NotNull String fieldName) {
        PropertyDescriptor descriptor = getPropertyDescriptorsByFieldNames().get(fieldName);
        if (descriptor != null) return descriptor;
        return super.getPropertyByFieldReference(fieldName);
    }

    public List<VariableDescriptor> getDeclaredVariables() {
        List<VariableDescriptor> result = Lists.newArrayList();
        for (DeclarationDescriptor descriptor : getVariableClassOrNamespaceDescriptors().values()) {
            if (descriptor instanceof VariableDescriptor) {
                VariableDescriptor variableDescriptor = (VariableDescriptor) descriptor;
                result.add(variableDescriptor);
            }
        }
        return result;
    }

    public boolean hasDeclaredItems() {
        return variableClassOrNamespaceDescriptors != null  && !variableClassOrNamespaceDescriptors.isEmpty();
    }

    @Override
    public WritableScopeImpl setDebugName(@NotNull String debugName) {
        super.setDebugName(debugName);
        return this;
    }
}