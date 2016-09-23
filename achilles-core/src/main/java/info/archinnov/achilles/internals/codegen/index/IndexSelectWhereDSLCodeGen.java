/*
 * Copyright (C) 2012-2016 DuyHai DOAN
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

package info.archinnov.achilles.internals.codegen.index;

import static info.archinnov.achilles.internals.parser.TypeUtils.*;
import static info.archinnov.achilles.internals.utils.NamingHelper.upperCaseFirst;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.Modifier;

import com.squareup.javapoet.*;

import info.archinnov.achilles.annotations.SASI;
import info.archinnov.achilles.internals.codegen.dsl.BaseSingleColumnRestriction;
import info.archinnov.achilles.internals.codegen.dsl.MultiColumnsSliceRestrictionCodeGen;
import info.archinnov.achilles.internals.codegen.dsl.select.SelectWhereDSLCodeGen;
import info.archinnov.achilles.internals.codegen.meta.EntityMetaCodeGen.EntityMetaSignature;
import info.archinnov.achilles.internals.metamodel.index.IndexImpl;
import info.archinnov.achilles.internals.metamodel.index.IndexType;
import info.archinnov.achilles.internals.parser.context.GlobalParsingContext;
import info.archinnov.achilles.internals.parser.context.SASIInfoContext;

public abstract class IndexSelectWhereDSLCodeGen extends SelectWhereDSLCodeGen
        implements BaseSingleColumnRestriction, MultiColumnsSliceRestrictionCodeGen {

    public abstract void buildSASIIndexRelation(TypeSpec.Builder indexSelectWhereBuilder,
                                                EntityMetaSignature signature,
                                                String parentClassName,
                                                ClassSignatureInfo lastSignature,
                                                ReturnType returnType);

    public List<TypeSpec> buildWhereClasses(GlobalParsingContext context, EntityMetaSignature signature) {

        final ClassSignatureParams classSignatureParams = ClassSignatureParams.of(INDEX_SELECT_DSL_SUFFIX,
                INDEX_SELECT_WHERE_DSL_SUFFIX, INDEX_SELECT_END_DSL_SUFFIX,
                ABSTRACT_SELECT_WHERE_PARTITION, ABSTRACT_INDEX_SELECT_WHERE);

        final ClassSignatureParams typedMapClassSignatureParams = ClassSignatureParams.of(INDEX_SELECT_DSL_SUFFIX,
                INDEX_SELECT_WHERE_TYPED_MAP_DSL_SUFFIX, INDEX_SELECT_END_TYPED_MAP_DSL_SUFFIX,
                ABSTRACT_SELECT_WHERE_PARTITION_TYPED_MAP, ABSTRACT_INDEX_SELECT_WHERE_TYPED_MAP);

        final List<TypeSpec> typeSpecs = buildWhereClassesInternal(signature, classSignatureParams);
        typeSpecs.addAll(buildWhereClassesInternal(signature, typedMapClassSignatureParams));

        typeSpecs.addAll(generateExtraWhereClasses(context, signature,
                getPartitionKeysSignatureInfo(signature.fieldMetaSignatures),
                getClusteringColsSignatureInfo(signature.fieldMetaSignatures)));

        return typeSpecs;
    }

    public List<TypeSpec> buildWhereClassesInternal(EntityMetaSignature signature, ClassSignatureParams classSignatureParams) {

        List<TypeSpec> typeSpecs = new ArrayList<>();


        String indexSelectWhereClassName = signature.className + classSignatureParams.whereDslSuffix;
        final TypeSpec.Builder indexSelectWhereBuilder = TypeSpec.classBuilder(indexSelectWhereClassName)
                .superclass(classSignatureParams.abstractWherePartitionType)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(buildWhereConstructor(SELECT_DOT_WHERE));

        final String endClassName = signature.endClassName(classSignatureParams.endDslSuffix);
        final TypeName endTypeName = ClassName.get(DSL_PACKAGE, endClassName);
        final TypeName endReturnTypeName = ClassName.get(DSL_PACKAGE, signature.endReturnType(classSignatureParams.dslSuffix, classSignatureParams.endDslSuffix));
        final TypeName abstractEndType = genericType(classSignatureParams.abstractWhereType, endReturnTypeName, signature.entityRawClass);

        final ClassSignatureInfo lastSignature = ClassSignatureInfo.of(endTypeName, endReturnTypeName, abstractEndType, endClassName);

        String parentClassName = signature.indexSelectClassName()
                + "." + signature.className + classSignatureParams.whereDslSuffix;

        final List<IndexFieldSignatureInfo> nativeIndexCols = getIndexedColsSignatureInfo(IndexImpl.NATIVE, signature.fieldMetaSignatures);
        nativeIndexCols.forEach(x -> buildNativeIndexRelation(indexSelectWhereBuilder,
                                                    x,
                                                    parentClassName,
                                                    lastSignature,
                                                    ReturnType.NEW));

        buildSASIIndexRelation(indexSelectWhereBuilder, signature, parentClassName, lastSignature, ReturnType.NEW);
        typeSpecs.add(indexSelectWhereBuilder.build());
        typeSpecs.add(buildSelectEndClass(signature, lastSignature, classSignatureParams));

        return typeSpecs;
    }

    public TypeSpec buildSelectEndClass(EntityMetaSignature signature, ClassSignatureInfo lastSignature, ClassSignatureParams classSignatureParams) {

        final Optional<FieldSignatureInfo> firstClustering = getClusteringColsSignatureInfo(signature.fieldMetaSignatures)
                .stream()
                .limit(1)
                .findFirst();

        final TypeSpec.Builder builder = TypeSpec.classBuilder(lastSignature.className)
                .superclass(lastSignature.superType)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(buildWhereConstructor(SELECT_DOT_WHERE))
                .addMethod(buildGetEntityClass(signature))
                .addMethod(buildGetMetaInternal(signature.entityRawClass))
                .addMethod(buildGetRte())
                .addMethod(buildGetOptions())
                .addMethod(buildGetBoundValuesInternal())
                .addMethod(buildGetEncodedBoundValuesInternal())
                .addMethod(buildLimit(lastSignature))
                .addMethod(buildGetThis(lastSignature.returnClassType));

        augmentSelectEndClass(builder, lastSignature);

        final List<FieldSignatureInfo> partitionKeys = getPartitionKeysSignatureInfo(signature.fieldMetaSignatures);
        final List<FieldSignatureInfo> clusteringCols = getClusteringColsSignatureInfo(signature.fieldMetaSignatures);

        final List<IndexFieldSignatureInfo> nativeIndexCols = getIndexedColsSignatureInfo(IndexImpl.NATIVE, signature.fieldMetaSignatures);

        partitionKeys.forEach(x -> this.buildPartitionKeyRelation(builder, signature,x, lastSignature, classSignatureParams));
        clusteringCols.forEach(x -> this.buildClusteringColumnRelation(builder, signature,x, lastSignature, classSignatureParams));

        String parentClassName = signature.indexSelectClassName()
                + "." + signature.className + classSignatureParams.endDslSuffix;

        nativeIndexCols.forEach(x -> buildNativeIndexRelation(builder, x, parentClassName, lastSignature, ReturnType.THIS));

        buildSASIIndexRelation(builder, signature, parentClassName, lastSignature, ReturnType.THIS);

        addMultipleColumnsSliceRestrictions(builder, parentClassName, clusteringCols, lastSignature, ReturnType.THIS);

        maybeBuildOrderingBy(lastSignature, firstClustering, builder);

        return builder.build();
    }

    public void buildNativeIndexRelation(TypeSpec.Builder indexSelectWhereBuilder,
                                         IndexFieldSignatureInfo indexFieldInfo,
                                         String parentClassName,
                                         ClassSignatureInfo lastSignature,
                                         ReturnType returnType) {

        final String relationClassName = upperCaseFirst(indexFieldInfo.fieldName) + DSL_RELATION_SUFFIX;
        TypeName relationClassTypeName = ClassName.get(DSL_PACKAGE, parentClassName + "." + relationClassName);

        final TypeSpec.Builder relationClassBuilder = TypeSpec.classBuilder(relationClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        final IndexType indexType = indexFieldInfo.indexInfo.type;

        switch (indexType) {
            case COLLECTION:
                relationClassBuilder.addMethod(buildIndexRelationForCollection(indexFieldInfo, lastSignature.returnClassType, returnType));
                break;
            case MAP_KEY:
                relationClassBuilder.addMethod(buildIndexRelationForMapKey(indexFieldInfo, lastSignature.returnClassType, returnType));
                break;
            case MAP_VALUE:
                relationClassBuilder.addMethod(buildIndexRelationForMapValue(indexFieldInfo, lastSignature.returnClassType, returnType));
                break;
            case MAP_ENTRY:
                relationClassBuilder.addMethod(buildIndexRelationForMapEntry(indexFieldInfo, lastSignature.returnClassType, returnType));
                break;
            case FULL:
            case NORMAL:
                relationClassBuilder.addMethod(buildColumnRelation(EQ, lastSignature.returnClassType, indexFieldInfo, returnType));
                break;
            case NONE:
                break;
        }

        augmentRelationClassForWhereClause(relationClassBuilder, indexFieldInfo, lastSignature, returnType);

        indexSelectWhereBuilder.addType(relationClassBuilder.build());

        indexSelectWhereBuilder.addMethod(buildRelationMethod(indexFieldInfo.fieldName, relationClassTypeName));
    }

    public MethodSpec buildIndexRelationForMapEntry(IndexFieldSignatureInfo indexFieldInfo, TypeName returnClassType, ReturnType returnType) {
        final String paramKey = indexFieldInfo.fieldName + "_key";
        final String paramValue = indexFieldInfo.fieldName + "_value";

        final MethodSpec.Builder builder = MethodSpec.methodBuilder("ContainsEntry")
                .addJavadoc("Generate a SELECT ... FROM ... WHERE ... <strong>$L[?] = ?</strong>", indexFieldInfo.quotedCqlColumn)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "static-access").build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(indexFieldInfo.indexMetaSignature.mapKeyType, paramKey)
                .addParameter(indexFieldInfo.indexMetaSignature.mapValueType, paramValue)
                .addStatement("where.and($T.of($S, $T.bindMarker($S), $T.bindMarker($S)))",
                        MAP_ENTRY_CLAUSE, indexFieldInfo.quotedCqlColumn,
                        QUERY_BUILDER, paramKey,
                        QUERY_BUILDER, paramValue)
                .addStatement("boundValues.add($N)", paramKey)
                .addStatement("boundValues.add($N)", paramValue)
                .addStatement("encodedValues.add(meta.$L.encodeSingleKeyElement($N))", indexFieldInfo.fieldName, paramKey)
                .addStatement("encodedValues.add(meta.$L.encodeSingleValueElement($N))", indexFieldInfo.fieldName, paramValue)
                .returns(returnClassType);

        if(returnType == ReturnType.THIS) {
            return builder.addStatement("return $T.this", returnClassType).build();
        } else {
            return builder.addStatement("return new $T(where)", returnClassType).build();
        }
    }

    protected MethodSpec buildIndexRelationForMapKey(IndexFieldSignatureInfo indexFieldInfo, TypeName returnClassType, ReturnType returnType) {
        final String param = indexFieldInfo.fieldName + "_key";

        final MethodSpec.Builder builder = MethodSpec.methodBuilder("ContainsKey")
                .addJavadoc("Generate a SELECT ... FROM ... WHERE ... <strong>$L CONTAINS KEY ?</strong>", indexFieldInfo.quotedCqlColumn)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "static-access").build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(indexFieldInfo.indexMetaSignature.mapKeyType, param)
                .addStatement("where.and($T.containsKey($S, $T.bindMarker($S)))",
                        QUERY_BUILDER, indexFieldInfo.quotedCqlColumn, QUERY_BUILDER, indexFieldInfo.quotedCqlColumn)
                .addStatement("boundValues.add($N)", param)
                .addStatement("encodedValues.add(meta.$L.encodeSingleKeyElement($N))", indexFieldInfo.fieldName, param)
                .returns(returnClassType);

        if(returnType == ReturnType.THIS) {
            return builder.addStatement("return $T.this", returnClassType).build();
        } else {
            return builder.addStatement("return new $T(where)", returnClassType).build();
        }
    }

    protected MethodSpec buildIndexRelationForMapValue(IndexFieldSignatureInfo indexFieldInfo, TypeName returnClassType, ReturnType returnType) {
        final String param = indexFieldInfo.fieldName + "_value";

        final MethodSpec.Builder builder = MethodSpec.methodBuilder("ContainsValue")
                .addJavadoc("Generate a SELECT ... FROM ... WHERE ... <strong>$L CONTAINS ?</strong>", indexFieldInfo.quotedCqlColumn)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "static-access").build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(indexFieldInfo.indexMetaSignature.mapValueType, param)
                .addStatement("where.and($T.contains($S, $T.bindMarker($S)))",
                        QUERY_BUILDER, indexFieldInfo.quotedCqlColumn, QUERY_BUILDER, indexFieldInfo.quotedCqlColumn)
                .addStatement("boundValues.add($N)", param)
                .addStatement("encodedValues.add(meta.$L.encodeSingleValueElement($N))", indexFieldInfo.fieldName, param)
                .returns(returnClassType);

        if(returnType == ReturnType.THIS) {
            return builder.addStatement("return $T.this", returnClassType).build();
        } else {
            return builder.addStatement("return new $T(where)", returnClassType).build();
        }
    }

    protected MethodSpec buildIndexRelationForCollection(IndexFieldSignatureInfo indexFieldInfo, TypeName returnClassType, ReturnType returnType) {
        final String param = indexFieldInfo.fieldName + "_element";
        final MethodSpec.Builder builder = MethodSpec.methodBuilder("Contains")
                .addJavadoc("Generate a SELECT ... FROM ... WHERE ... <strong>$L CONTAINS ?</strong>", indexFieldInfo.quotedCqlColumn)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "static-access").build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(indexFieldInfo.indexMetaSignature.collectionElementType, param)
                .addStatement("where.and($T.contains($S, $T.bindMarker($S)))",
                        QUERY_BUILDER, indexFieldInfo.quotedCqlColumn, QUERY_BUILDER, indexFieldInfo.quotedCqlColumn)
                .addStatement("boundValues.add($N)", param)
                .addStatement("encodedValues.add(meta.$L.encodeSingleElement($N))", indexFieldInfo.fieldName, param)
                .returns(returnClassType);
        if(returnType == ReturnType.THIS) {
            return builder.addStatement("return $T.this", returnClassType).build();
        } else {
            return builder.addStatement("return new $T(where)", returnClassType).build();
        }
    }

    private void buildPartitionKeyRelation(TypeSpec.Builder builder,
                                          EntityMetaSignature signature,
                                          FieldSignatureInfo partitionInfo,
                                          ClassSignatureInfo lastSignature,
                                          ClassSignatureParams classSignatureParams) {

        final String relationClassName = upperCaseFirst(partitionInfo.fieldName) + DSL_RELATION_SUFFIX;
        TypeName relationClassTypeName = ClassName.get(DSL_PACKAGE, signature.indexSelectClassName()
                + "." + signature.className + classSignatureParams.endDslSuffix
                + "." + relationClassName);

        final TypeSpec.Builder relationClassBuilder = TypeSpec.classBuilder(relationClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(buildColumnRelation(EQ, lastSignature.returnClassType, partitionInfo, ReturnType.THIS))
                .addMethod(buildColumnInVarargs(lastSignature.returnClassType, partitionInfo, ReturnType.THIS));

        augmentRelationClassForWhereClause(relationClassBuilder, partitionInfo, lastSignature, ReturnType.NEW);

        builder
                .addType(relationClassBuilder.build())
                .addMethod(buildRelationMethod(partitionInfo.fieldName, relationClassTypeName));
    }

    private void buildClusteringColumnRelation(TypeSpec.Builder builder,
                                              EntityMetaSignature signature,
                                              FieldSignatureInfo clusteringColumnInfo,
                                              ClassSignatureInfo lastSignature,
                                              ClassSignatureParams classSignatureParams) {

        final String relationClassName = upperCaseFirst(clusteringColumnInfo.fieldName) + DSL_RELATION_SUFFIX;
        TypeName relationClassTypeName = ClassName.get(DSL_PACKAGE, signature.indexSelectClassName()
                + "." + signature.className + classSignatureParams.endDslSuffix
                + "." + relationClassName);

        TypeSpec.Builder relationClassBuilder = TypeSpec.classBuilder(relationClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(buildColumnRelation(EQ, lastSignature.returnClassType, clusteringColumnInfo, ReturnType.THIS));

        addSingleColumnSliceRestrictions(relationClassBuilder, clusteringColumnInfo, lastSignature, lastSignature, ReturnType.THIS);

        augmentRelationClassForWhereClause(relationClassBuilder, clusteringColumnInfo, lastSignature, ReturnType.NEW);

        builder.addType(relationClassBuilder.build())
                .addMethod(buildRelationMethod(clusteringColumnInfo.fieldName, relationClassTypeName));
    }
}
