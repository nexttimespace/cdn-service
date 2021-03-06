/*
 * Copyright 2018 Next Time Space.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.nexttimespace.cdnservice.reader;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.stereotype.Component;

import com.nexttimespace.cdnservice.reader.data.DirectoryReaderObjects;
import com.nexttimespace.cdnservice.reader.data.ReaderObject;
import com.nexttimespace.cdnservice.utility.UtilityComponent;
import com.nexttimespace.cdnservice.utility.UtilityFunctions;

@Component
public class DirectoryReader extends MasterReader {
	@Autowired
	UtilityComponent utilityComponent;
	
	@Autowired
	TimerCacheCleaner timerCacheCleaner;
	
	@Autowired
	CacheManager cacheManager;
	
	private Logger logger = Logger.getLogger(DirectoryReader.class);
	private Map<String, DirectoryReaderObjects> directoryReaders = new LinkedHashMap<>();
	private Properties appConf;
	private boolean setupDone = false;
	
	public void setInit() throws Exception {
		if(!setupDone) {
			appConf = utilityComponent.getConfProperties();
			String type = "";
			int typeArrayIndex = 0;
			while((type = appConf.getProperty(String.format("repo[%s].type", typeArrayIndex))) != null) {
				String readerKey = String.format("repo[%s]", typeArrayIndex);
				String alias = appConf.getProperty(readerKey + ".alias");
				if(type.equals("directory")) {
					DirectoryReaderObjects directoryReaderObjects = new DirectoryReaderObjects();
					directoryReaderObjects.setAlias(alias);
					boolean cachable = Boolean.valueOf(appConf.getProperty(readerKey + ".cache-manager.enable"));
					if(cachable) {
						String cacheClearType = appConf.getProperty(readerKey + ".cache-manager.clear-strategy.type");
						if(cacheClearType.equals("timer")) {
							Long clearTime = Long.parseLong(appConf.getProperty(readerKey + ".cache-manager.clear-strategy.tic"));
							timerCacheCleaner.createCacheClearJob(this, alias, clearTime);
						}
					}
					directoryReaderObjects.setCachable(cachable);
					directoryReaderObjects.setDirectoryPath(appConf.getProperty(readerKey + ".directory.path"));
					
					String responseHeader = appConf.getProperty(readerKey + ".response.header");
					String[] responseHeaderArr = null;
					if(responseHeader != null && (responseHeaderArr = responseHeader.split("\\|")).length >= 2) {
						directoryReaderObjects.setResponseHeader(responseHeaderArr);
					}
					directoryReaderObjects.setTraffic(appConf.getProperty(readerKey + ".traffic"));
					directoryReaders.put(alias, directoryReaderObjects);
				}
				typeArrayIndex++;
			}
			setupDone = true;
			//setupCacheManager();
		}
	}
	
	@Cacheable(cacheNames="directoryContent", unless="#result[1].equals(\"0\")")
	public String[] getContent(String alias, String path) throws Exception {
		DirectoryReaderObjects readOb = directoryReaders.get(alias);
		String[] response = new String[2];
		response[0] = UtilityFunctions.readFile(readOb.getDirectoryPath() + path);
		response[1] = readOb.isCachable() && response[0].length() > 0 ? "1" : "0";
		return response;
	}

	@Override
	public void clearCache(String alias) {
		ConcurrentHashMap<SimpleKey , String[]> caches = (ConcurrentHashMap) cacheManager.getCache("directoryContent").getNativeCache();
		caches.keySet().forEach(simpleKey -> {
			if(simpleKey.toString().startsWith("SimpleKey ["+alias)) cacheManager.getCache("directoryContent").evict(simpleKey);
		});
		logger.info("Directory cache cleared for alias: " + alias);
	}

	@Override
	public List<ReaderObject> getReaderObject() {
		return new ArrayList<ReaderObject>(directoryReaders.values());
	}
}
