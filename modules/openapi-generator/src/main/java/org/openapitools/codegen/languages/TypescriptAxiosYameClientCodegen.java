package org.openapitools.codegen.languages;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.*;

import java.io.File;
import java.util.*;

import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypescriptAxiosYameClientCodegen extends TypeScriptAxiosClientCodegen implements CodegenConfig {
    public static final String PROJECT_NAME = "projectName";
    private static final String GENERIC_TYPE_PREFIX = "genericTypePrefix";

    private final Logger LOGGER = LoggerFactory.getLogger(TypescriptAxiosYameClientCodegen.class);

    private List<String> genericTypePrefixes;

    private Map<String, ModelsMap> genericModels = new HashMap<>();

    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    public String getName() {
        return "typescript-axios-yame";
    }

    public String getHelp() {
        return "支持泛型";
    }

    public void setGenericTypePrefixes(List<String> genericTypePrefixes) {
        if (genericTypePrefixes == null) {
            this.genericTypePrefixes = Collections.emptyList();
            return;
        }

        this.genericTypePrefixes = genericTypePrefixes
                .stream()
                .filter(StringUtils::isNotBlank)
                .sorted(Comparator.comparing(String::length).reversed())
                .toList();
    }

    @Override
    public void processOpts() {
        super.processOpts();
        convertPropertyToTypeAndWriteBack(GENERIC_TYPE_PREFIX, s -> Arrays.stream(StringUtils.split(s, "|")).toList(), this::setGenericTypePrefixes);
    }

    public TypescriptAxiosYameClientCodegen() {
        super();
        typeMapping.put("Long", "number");
    }

    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        Map<String, ModelsMap> allModels = super.postProcessAllModels(objs);

        //过滤掉泛型类型
        Map<String, ModelsMap> result = new HashMap<>();
        for (Map.Entry<String, ModelsMap> entry : allModels.entrySet()) {
            String modelName = entry.getKey();
            String genericType = getGenericType(modelName);
            if (genericType != null) {
                addGenericModels(entry.getValue(), genericType);
                continue;
            }
            result.put(modelName, entry.getValue());
        }

        if (!this.genericModels.isEmpty()) {
            this.supportingFiles.add(new SupportingFile("genericModels.mustache", modelPackage().replace('.', File.separatorChar), "generic-models.ts"));
        }

        return result;
    }

    private void addGenericModels(ModelsMap modelsMap, String genericType) {
        // 保存该泛型类型
        if (genericModels.containsKey(genericType)) {
            return;
        }

        ModelMap modelMap = modelsMap.getModels().get(0);
        CodegenModel codegenModel = modelMap.getModel();
        dealCodegenModel(genericType, codegenModel);
        genericModels.put(genericType, modelsMap);
    }

    protected void dealCodegenModel(String genericType, CodegenModel codegenModel) {
        // 处理类名
        String classnameOrigin = codegenModel.classname;
        String classname = getClassName(genericType, classnameOrigin);
        codegenModel.setClassname(classname);

        String typeName = StringUtils.substring(classnameOrigin, genericType.length());

        for (CodegenProperty property : codegenModel.vars) {
            if (Objects.equals(property.dataType, typeName)) {
                property.dataType = "T";
                continue;
            }

            //类型还是泛型
            var genericTypeProperty = getGenericType(property.dataType);
            if (genericTypeProperty != null) {
                property.dataType = String.format("%s<T>", genericTypeProperty);
                continue;
            }

            //容器类型 Array
            if(property.isContainer){
                if(Objects.equals(property.complexType, typeName)){
                    property.dataType = property.dataType.replace(typeName, "T");
                }
            }
        }
    }

    private String getClassName(String genericType, String classname) {
        return String.format("%s<T>", genericType);
    }

    public String getGenericType(String modelName) {
        if (modelName == null) {
            return null;
        }

        List<String> genericTypePrefix = getGenericTypePrefixes();
        for (String prefix : genericTypePrefix) {
            if (modelName.startsWith(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    /**
     * 获取通用类型前缀列表
     * 该方法用于返回一个预定义的字符串列表，这些字符串代表了通用类型的主要前缀
     * 这些前缀用于在系统中标识和处理不同的数据类型，如分页结果、列表结果等
     *
     * @return List<String> 返回一个包含通用类型前缀的不可变列表
     */
    protected List<String> getGenericTypePrefixes() {
        return this.genericTypePrefixes;
    }

    @Override
    public void addOperationToGroup(String tag, String resourcePath, Operation operation, CodegenOperation co, Map<String, List<CodegenOperation>> operations) {
        // ignore tag
        super.addOperationToGroup("", resourcePath, operation, co, operations);
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        var codegenOperation = super.fromOperation(path, httpMethod, operation, servers);
        var returnProperty = codegenOperation.returnProperty;
        var returnType = codegenOperation.returnType;
        var isComplexGenericType = false;
        String genericType = null;

        if (!Objects.equals(returnProperty.complexType, returnProperty.baseType)) {
            genericType = getGenericType(returnProperty.complexType);
            if (genericType != null) {
                isComplexGenericType = true;
                returnType = returnProperty.complexType;
            }
        }

        genericType = genericType == null ? getGenericType(returnType) : genericType;
        if (genericType != null) {
            var newReturnType = StringUtils.substring(returnType, genericType.length());
            codegenOperation.imports.remove(returnType);
            if (this.typeMapping.containsKey(newReturnType)) {
                newReturnType = this.typeMapping.get(newReturnType);
            } else {
                codegenOperation.imports.add(newReturnType);
            }

            // 是否是复杂泛型类型
            if (isComplexGenericType) {
                codegenOperation.returnType = String.format("%s<%s<%s>>", returnProperty.baseType, genericType, newReturnType);
            } else {
                codegenOperation.returnType = String.format("%s<%s>", genericType, newReturnType);
            }
        }

        return codegenOperation;
    }

    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        Map<String, Object> bundle = super.postProcessSupportingFileData(objs);

        List<ModelsMap> models = new ArrayList<>();
        for (Map.Entry<String, ModelsMap> entry : this.genericModels.entrySet()) {
            models.add(entry.getValue());
        }

        bundle.put("genericModels", models);
        bundle.put("genericModelsNotEmpty", !models.isEmpty());
        return bundle;
    }


}
