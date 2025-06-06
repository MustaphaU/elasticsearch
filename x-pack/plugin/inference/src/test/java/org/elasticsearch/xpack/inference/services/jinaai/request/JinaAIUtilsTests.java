/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services.jinaai.request;

import org.elasticsearch.test.ESTestCase;

import static org.hamcrest.Matchers.is;

public class JinaAIUtilsTests extends ESTestCase {

    public void testCreateRequestSourceHeader() {
        var requestSourceHeader = JinaAIUtils.createRequestSourceHeader();

        assertThat(requestSourceHeader.getName(), is("Request-Source"));
        assertThat(requestSourceHeader.getValue(), is("unspecified:elasticsearch"));
    }

}
