package com.admin.management.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/** Base class for integration tests that use MockMvc on the test database. */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest extends AbstractIntegrationTest {

}
