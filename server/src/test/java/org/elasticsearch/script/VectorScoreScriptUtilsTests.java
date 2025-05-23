/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.script;

import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.index.IndexVersions;
import org.elasticsearch.index.mapper.vectors.BinaryDenseVectorScriptDocValuesTests;
import org.elasticsearch.index.mapper.vectors.DenseVectorFieldMapper.ElementType;
import org.elasticsearch.index.mapper.vectors.KnnDenseVectorScriptDocValuesTests;
import org.elasticsearch.script.VectorScoreScriptUtils.CosineSimilarity;
import org.elasticsearch.script.VectorScoreScriptUtils.DotProduct;
import org.elasticsearch.script.VectorScoreScriptUtils.Hamming;
import org.elasticsearch.script.VectorScoreScriptUtils.L1Norm;
import org.elasticsearch.script.VectorScoreScriptUtils.L2Norm;
import org.elasticsearch.script.field.vectors.BinaryDenseVectorDocValuesField;
import org.elasticsearch.script.field.vectors.BitBinaryDenseVectorDocValuesField;
import org.elasticsearch.script.field.vectors.BitKnnDenseVectorDocValuesField;
import org.elasticsearch.script.field.vectors.ByteBinaryDenseVectorDocValuesField;
import org.elasticsearch.script.field.vectors.ByteKnnDenseVectorDocValuesField;
import org.elasticsearch.script.field.vectors.DenseVectorDocValuesField;
import org.elasticsearch.script.field.vectors.KnnDenseVectorDocValuesField;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.util.HexFormat;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VectorScoreScriptUtilsTests extends ESTestCase {

    public void testFloatVectorClassBindings() throws IOException {
        String fieldName = "vector";
        int dims = 5;
        float[] docVector = new float[] { 230.0f, 300.33f, -34.8988f, 15.555f, -200.0f };
        List<Number> queryVector = List.of(0.5f, 111.3f, -13.0f, 14.8f, -156.0f);
        List<Number> invalidQueryVector = List.of(0.5, 111.3);

        List<DenseVectorDocValuesField> fields = List.of(
            new BinaryDenseVectorDocValuesField(
                BinaryDenseVectorScriptDocValuesTests.wrap(
                    new float[][] { docVector },
                    ElementType.FLOAT,
                    IndexVersions.MINIMUM_READONLY_COMPATIBLE
                ),
                "test",
                ElementType.FLOAT,
                dims,
                IndexVersions.MINIMUM_READONLY_COMPATIBLE
            ),
            new BinaryDenseVectorDocValuesField(
                BinaryDenseVectorScriptDocValuesTests.wrap(new float[][] { docVector }, ElementType.FLOAT, IndexVersion.current()),
                "test",
                ElementType.FLOAT,
                dims,
                IndexVersion.current()
            ),
            new KnnDenseVectorDocValuesField(KnnDenseVectorScriptDocValuesTests.wrap(new float[][] { docVector }), "test", dims)
        );
        for (DenseVectorDocValuesField field : fields) {
            field.setNextDocId(0);

            ScoreScript scoreScript = mock(ScoreScript.class);
            when(scoreScript.field("vector")).thenAnswer(mock -> field);

            // Test cosine similarity explicitly, as it must perform special logic on top of the doc values
            CosineSimilarity function = new CosineSimilarity(scoreScript, queryVector, fieldName);
            float cosineSimilarityExpected = 0.790f;
            assertEquals(
                "cosineSimilarity result is not equal to the expected value!",
                cosineSimilarityExpected,
                function.cosineSimilarity(),
                0.001
            );

            // Test normalization for cosineSimilarity
            float[] queryVectorArray = new float[queryVector.size()];
            for (int i = 0; i < queryVectorArray.length; i++) {
                queryVectorArray[i] = queryVector.get(i).floatValue();
            }
            assertEquals(
                "cosineSimilarity result is not equal to the expected value!",
                cosineSimilarityExpected,
                field.getInternal().cosineSimilarity(queryVectorArray, true),
                0.001
            );

            // Check each function rejects query vectors with the wrong dimension
            IllegalArgumentException e = expectThrows(
                IllegalArgumentException.class,
                () -> new DotProduct(scoreScript, invalidQueryVector, fieldName)
            );
            assertThat(
                e.getMessage(),
                containsString("query vector has a different number of dimensions [2] than the document vectors [5]")
            );
            e = expectThrows(IllegalArgumentException.class, () -> new CosineSimilarity(scoreScript, invalidQueryVector, fieldName));
            assertThat(
                e.getMessage(),
                containsString("query vector has a different number of dimensions [2] than the document vectors [5]")
            );
            e = expectThrows(IllegalArgumentException.class, () -> new L1Norm(scoreScript, invalidQueryVector, fieldName));
            assertThat(
                e.getMessage(),
                containsString("query vector has a different number of dimensions [2] than the document vectors [5]")
            );
            e = expectThrows(IllegalArgumentException.class, () -> new L2Norm(scoreScript, invalidQueryVector, fieldName));
            assertThat(
                e.getMessage(),
                containsString("query vector has a different number of dimensions [2] than the document vectors [5]")
            );

            e = expectThrows(IllegalArgumentException.class, () -> new Hamming(scoreScript, queryVector, fieldName));
            assertThat(e.getMessage(), containsString("hamming distance is only supported for byte or bit vectors"));

            e = expectThrows(IllegalArgumentException.class, () -> new Hamming(scoreScript, invalidQueryVector, fieldName));
            assertThat(e.getMessage(), containsString("hamming distance is only supported for byte or bit vectors"));

            // Check scripting infrastructure integration
            DotProduct dotProduct = new DotProduct(scoreScript, queryVector, fieldName);
            assertEquals(65425.6249, dotProduct.dotProduct(), 0.001);
            assertEquals(485.1837, new L1Norm(scoreScript, queryVector, fieldName).l1norm(), 0.001);
            assertEquals(301.3614, new L2Norm(scoreScript, queryVector, fieldName).l2norm(), 0.001);
            when(scoreScript._getDocId()).thenReturn(1);
            e = expectThrows(IllegalArgumentException.class, dotProduct::dotProduct);
            assertEquals("A document doesn't have a value for a vector field!", e.getMessage());
        }
    }

    public void testByteVectorClassBindings() throws IOException {
        String fieldName = "vector";
        int dims = 5;
        float[] docVector = new float[] { 1, 127, -128, 5, -10 };
        List<Number> queryVector = List.of((byte) 1, (byte) 125, (byte) -12, (byte) 2, (byte) 4);
        List<Number> invalidQueryVector = List.of((byte) 1, (byte) 1);
        String hexidecimalString = HexFormat.of().formatHex(new byte[] { 1, 125, -12, 2, 4 });

        List<DenseVectorDocValuesField> fields = List.of(
            new ByteBinaryDenseVectorDocValuesField(
                BinaryDenseVectorScriptDocValuesTests.wrap(new float[][] { docVector }, ElementType.BYTE, IndexVersion.current()),
                "test",
                ElementType.BYTE,
                dims
            ),
            new ByteKnnDenseVectorDocValuesField(KnnDenseVectorScriptDocValuesTests.wrapBytes(new float[][] { docVector }), "test", dims)
        );
        for (DenseVectorDocValuesField field : fields) {
            field.setNextDocId(0);

            ScoreScript scoreScript = mock(ScoreScript.class);
            when(scoreScript.field(fieldName)).thenAnswer(mock -> field);

            // Test cosine similarity explicitly, as it must perform special logic on top of the doc values
            CosineSimilarity function = new CosineSimilarity(scoreScript, queryVector, fieldName);
            float cosineSimilarityExpected = 0.765f;
            assertEquals(
                "cosineSimilarity result is not equal to the expected value!",
                cosineSimilarityExpected,
                function.cosineSimilarity(),
                0.001
            );

            function = new CosineSimilarity(scoreScript, hexidecimalString, fieldName);
            assertEquals(
                "cosineSimilarity result is not equal to the expected value!",
                cosineSimilarityExpected,
                function.cosineSimilarity(),
                0.001
            );

            // Test normalization for cosineSimilarity
            float[] queryVectorArray = new float[queryVector.size()];
            for (int i = 0; i < queryVectorArray.length; i++) {
                queryVectorArray[i] = queryVector.get(i).floatValue();
            }
            assertEquals(
                "cosineSimilarity result is not equal to the expected value!",
                cosineSimilarityExpected,
                field.getInternal().cosineSimilarity(queryVectorArray, true),
                0.001
            );

            // Check each function rejects query vectors with the wrong dimension
            IllegalArgumentException e = expectThrows(
                IllegalArgumentException.class,
                () -> new DotProduct(scoreScript, invalidQueryVector, fieldName)
            );
            assertThat(
                e.getMessage(),
                containsString("query vector has a different number of dimensions [2] than the document vectors [5]")
            );
            e = expectThrows(IllegalArgumentException.class, () -> new CosineSimilarity(scoreScript, invalidQueryVector, fieldName));
            assertThat(
                e.getMessage(),
                containsString("query vector has a different number of dimensions [2] than the document vectors [5]")
            );
            e = expectThrows(IllegalArgumentException.class, () -> new L1Norm(scoreScript, invalidQueryVector, fieldName));
            assertThat(
                e.getMessage(),
                containsString("query vector has a different number of dimensions [2] than the document vectors [5]")
            );
            e = expectThrows(IllegalArgumentException.class, () -> new L2Norm(scoreScript, invalidQueryVector, fieldName));
            assertThat(
                e.getMessage(),
                containsString("query vector has a different number of dimensions [2] than the document vectors [5]")
            );
            e = expectThrows(IllegalArgumentException.class, () -> new Hamming(scoreScript, invalidQueryVector, fieldName));
            assertThat(
                e.getMessage(),
                containsString("query vector has a different number of dimensions [2] than the document vectors [5]")
            );

            // Check scripting infrastructure integration
            assertEquals(17382.0, new DotProduct(scoreScript, queryVector, fieldName).dotProduct(), 0.001);
            assertEquals(17382.0, new DotProduct(scoreScript, hexidecimalString, fieldName).dotProduct(), 0.001);
            assertEquals(135.0, new L1Norm(scoreScript, queryVector, fieldName).l1norm(), 0.001);
            assertEquals(135.0, new L1Norm(scoreScript, hexidecimalString, fieldName).l1norm(), 0.001);
            assertEquals(116.897, new L2Norm(scoreScript, queryVector, fieldName).l2norm(), 0.001);
            assertEquals(116.897, new L2Norm(scoreScript, hexidecimalString, fieldName).l2norm(), 0.001);
            assertEquals(13.0, new Hamming(scoreScript, queryVector, fieldName).hamming(), 0.001);
            assertEquals(13.0, new Hamming(scoreScript, hexidecimalString, fieldName).hamming(), 0.001);
            DotProduct dotProduct = new DotProduct(scoreScript, queryVector, fieldName);
            when(scoreScript._getDocId()).thenReturn(1);
            e = expectThrows(IllegalArgumentException.class, dotProduct::dotProduct);
            assertEquals("A document doesn't have a value for a vector field!", e.getMessage());
        }
    }

    public void testBitVectorClassBindingsDotProduct() throws IOException {
        String fieldName = "vector";
        int dims = 8;
        float[] docVector = new float[] { 124 };
        // 124 in binary is b01111100
        List<Number> queryVector = List.of((byte) 1, (byte) 125, (byte) -12, (byte) 2, (byte) 4, (byte) 1, (byte) 125, (byte) -12);
        List<Number> floatQueryVector = List.of(1.4f, -1.4f, 0.42f, 0.0f, 1f, -1f, -0.42f, 1.2f);
        List<Number> invalidQueryVector = List.of((byte) 1, (byte) 1);
        String hexidecimalString = HexFormat.of().formatHex(new byte[] { 124 });

        List<DenseVectorDocValuesField> fields = List.of(
            new BitBinaryDenseVectorDocValuesField(
                BinaryDenseVectorScriptDocValuesTests.wrap(new float[][] { docVector }, ElementType.BIT, IndexVersion.current()),
                "test",
                ElementType.BIT,
                dims
            ),
            new BitKnnDenseVectorDocValuesField(KnnDenseVectorScriptDocValuesTests.wrapBytes(new float[][] { docVector }), "test", dims)
        );
        for (DenseVectorDocValuesField field : fields) {
            field.setNextDocId(0);

            ScoreScript scoreScript = mock(ScoreScript.class);
            when(scoreScript.field(fieldName)).thenAnswer(mock -> field);

            // Test cosine similarity explicitly, as it must perform special logic on top of the doc values
            DotProduct function = new DotProduct(scoreScript, queryVector, fieldName);
            assertEquals("dotProduct result is not equal to the expected value!", -12 + 2 + 4 + 1 + 125, function.dotProduct(), 0.001);

            function = new DotProduct(scoreScript, floatQueryVector, fieldName);
            assertEquals(
                "dotProduct result is not equal to the expected value!",
                -1.4f + 0.42f + 0f + 1f - 1f,
                function.dotProduct(),
                0.001
            );

            function = new DotProduct(scoreScript, hexidecimalString, fieldName);
            assertEquals("dotProduct result is not equal to the expected value!", Integer.bitCount(124), function.dotProduct(), 0.0);

            // Check each function rejects query vectors with the wrong dimension
            IllegalArgumentException e = expectThrows(
                IllegalArgumentException.class,
                () -> new DotProduct(scoreScript, invalidQueryVector, fieldName)
            );
            assertThat(
                e.getMessage(),
                containsString(
                    "query vector has an incorrect number of dimensions. "
                        + "Must be [1] for bitwise operations, or [8] for byte wise operations: provided [2]."
                )
            );
        }
    }

    public void testByteVsFloatSimilarity() throws IOException {
        int dims = 5;
        float[] docVector = new float[] { 1f, 127f, -128f, 5f, -10f };
        List<Number> listFloatVector = List.of(1f, 125f, -12f, 2f, 4f);
        List<Number> listByteVector = List.of((byte) 1, (byte) 125, (byte) -12, (byte) 2, (byte) 4);
        float[] floatVector = new float[] { 1f, 125f, -12f, 2f, 4f };
        byte[] byteVector = new byte[] { (byte) 1, (byte) 125, (byte) -12, (byte) 2, (byte) 4 };

        List<DenseVectorDocValuesField> fields = List.of(
            new BinaryDenseVectorDocValuesField(
                BinaryDenseVectorScriptDocValuesTests.wrap(
                    new float[][] { docVector },
                    ElementType.FLOAT,
                    IndexVersions.MINIMUM_READONLY_COMPATIBLE
                ),
                "field0",
                ElementType.FLOAT,
                dims,
                IndexVersions.MINIMUM_READONLY_COMPATIBLE
            ),
            new BinaryDenseVectorDocValuesField(
                BinaryDenseVectorScriptDocValuesTests.wrap(new float[][] { docVector }, ElementType.FLOAT, IndexVersion.current()),
                "field1",
                ElementType.FLOAT,
                dims,
                IndexVersion.current()
            ),
            new KnnDenseVectorDocValuesField(KnnDenseVectorScriptDocValuesTests.wrap(new float[][] { docVector }), "field2", dims),
            new ByteBinaryDenseVectorDocValuesField(
                BinaryDenseVectorScriptDocValuesTests.wrap(new float[][] { docVector }, ElementType.BYTE, IndexVersion.current()),
                "field3",
                ElementType.BYTE,
                dims
            ),
            new ByteKnnDenseVectorDocValuesField(KnnDenseVectorScriptDocValuesTests.wrapBytes(new float[][] { docVector }), "field4", dims)
        );
        for (DenseVectorDocValuesField field : fields) {
            field.setNextDocId(0);

            ScoreScript scoreScript = mock(ScoreScript.class);
            when(scoreScript.field("vector")).thenAnswer(mock -> field);

            int dotProductExpected = 17382;
            DotProduct dotProduct = new DotProduct(scoreScript, listFloatVector, "vector");
            assertEquals(field.getName(), dotProductExpected, dotProduct.dotProduct(), 0.001);
            dotProduct = new DotProduct(scoreScript, listByteVector, "vector");
            assertEquals(field.getName(), dotProductExpected, dotProduct.dotProduct(), 0.001);
            assertEquals(field.getName(), dotProductExpected, field.get().dotProduct(listFloatVector), 0.001);
            assertEquals(field.getName(), dotProductExpected, field.get().dotProduct(listByteVector), 0.001);
            switch (field.getElementType()) {
                case BYTE -> {
                    assertEquals(field.getName(), dotProductExpected, field.get().dotProduct(byteVector));
                    assertEquals(field.getName(), dotProductExpected, field.get().dotProduct(floatVector), 0.001);
                }
                case FLOAT -> {
                    assertEquals(field.getName(), dotProductExpected, field.get().dotProduct(floatVector), 0.001);
                    UnsupportedOperationException e = expectThrows(
                        UnsupportedOperationException.class,
                        () -> field.get().dotProduct(byteVector)
                    );
                    assertThat(e.getMessage(), containsString("use [double dotProduct(float[] queryVector)] instead"));
                }
            }
            ;

            int l1NormExpected = 135;
            L1Norm l1Norm = new L1Norm(scoreScript, listFloatVector, "vector");
            assertEquals(field.getName(), l1NormExpected, l1Norm.l1norm(), 0.001);
            l1Norm = new L1Norm(scoreScript, listByteVector, "vector");
            assertEquals(field.getName(), l1NormExpected, l1Norm.l1norm(), 0.001);
            assertEquals(field.getName(), l1NormExpected, field.get().l1Norm(listFloatVector), 0.001);
            assertEquals(field.getName(), l1NormExpected, field.get().l1Norm(listByteVector), 0.001);
            switch (field.getElementType()) {
                case BYTE -> {
                    assertEquals(field.getName(), l1NormExpected, field.get().l1Norm(byteVector));
                    UnsupportedOperationException e = expectThrows(
                        UnsupportedOperationException.class,
                        () -> field.get().l1Norm(floatVector)
                    );
                    assertThat(e.getMessage(), containsString("use [int l1Norm(byte[] queryVector)] instead"));
                }
                case FLOAT -> {
                    assertEquals(field.getName(), l1NormExpected, field.get().l1Norm(floatVector), 0.001);
                    UnsupportedOperationException e = expectThrows(
                        UnsupportedOperationException.class,
                        () -> field.get().l1Norm(byteVector)
                    );
                    assertThat(e.getMessage(), containsString("use [double l1Norm(float[] queryVector)] instead"));
                }
            }
            ;

            float l2NormExpected = 116.897f;
            L2Norm l2Norm = new L2Norm(scoreScript, listFloatVector, "vector");
            assertEquals(field.getName(), l2NormExpected, l2Norm.l2norm(), 0.001);
            l2Norm = new L2Norm(scoreScript, listByteVector, "vector");
            assertEquals(field.getName(), l2NormExpected, l2Norm.l2norm(), 0.001);
            assertEquals(field.getName(), l2NormExpected, field.get().l2Norm(listFloatVector), 0.001);
            assertEquals(field.getName(), l2NormExpected, field.get().l2Norm(listByteVector), 0.001);
            switch (field.getElementType()) {
                case BYTE -> {
                    assertEquals(field.getName(), l2NormExpected, field.get().l2Norm(byteVector), 0.001);
                    UnsupportedOperationException e = expectThrows(
                        UnsupportedOperationException.class,
                        () -> field.get().l2Norm(floatVector)
                    );
                    assertThat(e.getMessage(), containsString("use [double l2Norm(byte[] queryVector)] instead"));
                }
                case FLOAT -> {
                    assertEquals(field.getName(), l2NormExpected, field.get().l2Norm(floatVector), 0.001);
                    UnsupportedOperationException e = expectThrows(
                        UnsupportedOperationException.class,
                        () -> field.get().l2Norm(byteVector)
                    );
                    assertThat(e.getMessage(), containsString("use [double l2Norm(float[] queryVector)] instead"));
                }
            }
            ;

            float cosineSimilarityExpected = 0.765f;
            CosineSimilarity cosineSimilarity = new CosineSimilarity(scoreScript, listFloatVector, "vector");
            assertEquals(field.getName(), cosineSimilarityExpected, cosineSimilarity.cosineSimilarity(), 0.001);
            cosineSimilarity = new CosineSimilarity(scoreScript, listByteVector, "vector");
            assertEquals(field.getName(), cosineSimilarityExpected, cosineSimilarity.cosineSimilarity(), 0.001);
            assertEquals(field.getName(), cosineSimilarityExpected, field.get().cosineSimilarity(listFloatVector), 0.001);
            assertEquals(field.getName(), cosineSimilarityExpected, field.get().cosineSimilarity(listByteVector), 0.001);
            switch (field.getElementType()) {
                case BYTE -> {
                    assertEquals(field.getName(), cosineSimilarityExpected, field.get().cosineSimilarity(byteVector), 0.001);
                    assertEquals(field.getName(), cosineSimilarityExpected, field.get().cosineSimilarity(floatVector), 0.001);
                }
                case FLOAT -> {
                    assertEquals(field.getName(), cosineSimilarityExpected, field.get().cosineSimilarity(floatVector), 0.001);
                    UnsupportedOperationException e = expectThrows(
                        UnsupportedOperationException.class,
                        () -> field.get().cosineSimilarity(byteVector)
                    );
                    assertThat(
                        e.getMessage(),
                        containsString("use [double cosineSimilarity(float[] queryVector, boolean normalizeQueryVector)] instead")
                    );
                }
            }
        }
    }

    public void testByteBoundaries() throws IOException {
        String fieldName = "vector";
        int dims = 1;
        float[] docVector = new float[] { 0 };
        List<Number> greaterThanVector = List.of(128);
        List<Number> lessThanVector = List.of(-129);
        List<Number> decimalVector = List.of(0.5);

        List<DenseVectorDocValuesField> fields = List.of(
            new ByteBinaryDenseVectorDocValuesField(
                BinaryDenseVectorScriptDocValuesTests.wrap(new float[][] { docVector }, ElementType.BYTE, IndexVersion.current()),
                "test",
                ElementType.BYTE,
                dims
            ),
            new ByteKnnDenseVectorDocValuesField(KnnDenseVectorScriptDocValuesTests.wrapBytes(new float[][] { docVector }), "test", dims)
        );

        for (DenseVectorDocValuesField field : fields) {
            field.setNextDocId(0);

            ScoreScript scoreScript = mock(ScoreScript.class);
            when(scoreScript.field(fieldName)).thenAnswer(mock -> field);

            expectThrows(
                IllegalArgumentException.class,
                containsString(
                    "element_type [byte] vectors only support integers between [-128, 127] but found [128.0] at dim [0]; "
                        + "Preview of invalid vector: [128.0]"
                ),
                () -> new L1Norm(scoreScript, greaterThanVector, fieldName)
            );
            expectThrows(
                IllegalArgumentException.class,
                containsString(
                    "element_type [byte] vectors only support integers between [-128, 127] but found [128.0] at dim [0]; "
                        + "Preview of invalid vector: [128.0]"
                ),
                () -> new L2Norm(scoreScript, greaterThanVector, fieldName)
            );

            expectThrows(
                IllegalArgumentException.class,
                containsString(
                    "element_type [byte] vectors only support integers between [-128, 127] but found [-129.0] at dim [0]; "
                        + "Preview of invalid vector: [-129.0]"
                ),
                () -> new L1Norm(scoreScript, lessThanVector, fieldName)
            );
            expectThrows(
                IllegalArgumentException.class,
                containsString(
                    "element_type [byte] vectors only support integers between [-128, 127] but found [-129.0] at dim [0]; "
                        + "Preview of invalid vector: [-129.0]"
                ),
                () -> new L2Norm(scoreScript, lessThanVector, fieldName)
            );

            expectThrows(
                IllegalArgumentException.class,
                containsString(
                    "element_type [byte] vectors only support non-decimal values but found decimal value [0.5] at dim [0]; "
                        + "Preview of invalid vector: [0.5]"
                ),
                () -> new L1Norm(scoreScript, decimalVector, fieldName)
            );
            expectThrows(
                IllegalArgumentException.class,
                containsString(
                    "element_type [byte] vectors only support non-decimal values but found decimal value [0.5] at dim [0]; "
                        + "Preview of invalid vector: [0.5]"
                ),
                () -> new L2Norm(scoreScript, decimalVector, fieldName)
            );
        }
    }
}
