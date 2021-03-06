/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.identityasserter.switchcase;

import java.util.Iterator;
import java.util.ServiceLoader;

import org.apache.hadoop.gateway.deploy.ProviderDeploymentContributor;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class SwitchCaseIdentityAsserterDeploymentContributorTest {

  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader<ProviderDeploymentContributor> loader = ServiceLoader.load( ProviderDeploymentContributor.class );
    Iterator<ProviderDeploymentContributor> iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof SwitchCaseIdentityAsserterDeploymentContributor ) {
        return;
      }
    }
    fail( "Failed to find " + SwitchCaseIdentityAsserterDeploymentContributor.class.getName() + " via service loader." );
  }
}
