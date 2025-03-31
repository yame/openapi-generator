package org.openapitools.codegen.languages;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.*;

import java.util.*;

import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypescriptAxiosYameClientCodegen extends TypeScriptAxiosClientCodegen implements CodegenConfig {
    public static final String PROJECT_NAME = "projectName";
    private static final String GENERIC_TYPE_PREFIX = "genericTypePrefix";

    private final Logger LOGGER = LoggerFactory.getLogger(TypescriptAxiosYameClientCodegen.class);

    @Setter
    private List<String> genericTypePrefixes;

    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    public String getName() {
        return "typescript-axios-yame";
    }

    public String getHelp() {
        return "支持泛型";
    }

    @Override
    public void processOpts() {
        super.processOpts();
        convertPropertyToTypeAndWriteBack(GENERIC_TYPE_PREFIX, s -> Arrays.stream(StringUtils.split(s, ",")).toList(), this::setGenericTypePrefixes);
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
                continue;
            }

            result.put(modelName, entry.getValue());
        }

        return result;
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
        return this.genericTypePrefixes == null ? Collections.emptyList() : Collections.unmodifiableList(this.genericTypePrefixes);
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
}
