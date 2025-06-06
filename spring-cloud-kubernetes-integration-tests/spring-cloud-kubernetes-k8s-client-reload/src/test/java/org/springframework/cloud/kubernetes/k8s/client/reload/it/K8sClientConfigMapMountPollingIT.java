/*
 * Copyright 2013-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.k8s.client.reload.it;

import java.time.Duration;
import java.util.Map;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.commons.config.Constants;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.builder;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.retrySpec;

/**
 * @author wind57
 */
class K8sClientConfigMapMountPollingIT extends K8sClientReloadBase {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-k8s-client-reload";

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	private static CoreV1Api coreV1Api;

	@BeforeAll
	static void beforeAllLocal() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
		coreV1Api = new CoreV1Api();
		util.setUp(NAMESPACE);
		manifests(Phase.CREATE, util, NAMESPACE, IMAGE_NAME);
	}

	@AfterAll
	static void afterAll() {
		manifests(Phase.DELETE, util, NAMESPACE, IMAGE_NAME);
	}

	/**
	 * <pre>
	 *     - we have bootstrap disabled
	 *     - we will 'locate' property sources from config maps.
	 *     - there are no explicit config maps to search for, but what we will also read,
	 *     	 is 'spring.cloud.kubernetes.config.paths', which we have set to
	 *     	 '/tmp/application.properties'
	 *       in this test. That is populated by the volumeMounts (see mount/deployment.yaml)
	 *     - we first assert that we are actually reading the path based source via (1), (2) and (3).
	 *
	 *     - we then change the config map content, wait for k8s to pick it up and replace them
	 *     - our polling will then detect that change, and trigger a reload.
	 * </pre>
	 */
	@Test
	void test() throws Exception {
		// (1)
		Commons.waitForLogStatement("paths property sources : [/tmp/application.properties]", K3S, IMAGE_NAME);
		// (2)
		Commons.waitForLogStatement("will add file-based property source : /tmp/application.properties", K3S,
				IMAGE_NAME);
		// (3)
		WebClient webClient = builder().baseUrl("http://localhost:32321/configmap").build();
		String result = webClient.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		// we first read the initial value from the configmap
		assertThat(result).isEqualTo("as-mount-initial");

		// replace data in configmap and wait for k8s to pick it up
		// our polling will detect that and restart the app
		V1ConfigMap configMap = (V1ConfigMap) util.yaml("mount/configmap.yaml");
		configMap.setData(Map.of(Constants.APPLICATION_PROPERTIES, "from.properties.configmap.key=as-mount-changed"));
		coreV1Api.replaceNamespacedConfigMap("configmap-reload", NAMESPACE, configMap, null, null, null, null);

		Commons.waitForLogStatement("Detected change in config maps/secrets, reload will be triggered", K3S,
				IMAGE_NAME);

		await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(1)).until(() -> {
			String local = webClient.method(HttpMethod.GET)
				.retrieve()
				.bodyToMono(String.class)
				.retryWhen(retrySpec())
				.block();
			return "as-mount-changed".equals(local);
		});
	}

}
