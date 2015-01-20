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
package org.apache.hadoop.gateway.hdfs.dispatch;

import org.apache.hadoop.gateway.dispatch.GatewayDispatchFilter;
import org.apache.hadoop.gateway.ha.provider.HaProvider;
import org.apache.hadoop.gateway.ha.provider.HaServletContextListener;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

public class WebHdfsHaDispatchFilter extends GatewayDispatchFilter {

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    super.init(filterConfig);
    HaProvider haProvider = HaServletContextListener.getHaProvider(filterConfig.getServletContext());
    ((WebHdfsHaHttpClientDispatch)getDispatch()).setHaProvider(haProvider);
    getDispatch().init();
  }
}