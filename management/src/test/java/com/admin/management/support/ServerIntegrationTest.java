package com.admin.management.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/** Base class for tests that need a real servlet container, such as session expiry and lock-out timing. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public abstract class ServerIntegrationTest extends AbstractIntegrationTest {

}
