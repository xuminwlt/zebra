package com.dianping.zebra.filter;

import com.dianping.zebra.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Dozer on 9/2/14.
 */
public class DefaultFilterManager implements FilterManager {
	private static final String FILTER_KEY_NAME = "zebra.filter.";

	private static final String FILTER_PROPERTY_NAME = "META-INF/zebra-filter.properties";

	private final ConcurrentHashMap<String, String> aliasMap = new ConcurrentHashMap<String, String>();

	private final ConcurrentHashMap<String, List<JdbcFilter>> cachedFilterMap = new ConcurrentHashMap<String, List<JdbcFilter>>();

	@Override
	public void addFilter(String name, JdbcFilter filter) {
		List<JdbcFilter> filters;
		if (cachedFilterMap.containsKey(name)) {
			filters = cachedFilterMap.get(name);
		} else {
			filters = new ArrayList<JdbcFilter>();
			cachedFilterMap.put(name, filters);
		}

		filters.add(filter);
	}

	public void init() {
		try {
			Properties filterProperties = loadFilterConfig();
			for (Map.Entry<Object, Object> entry : filterProperties.entrySet()) {
				String key = (String) entry.getKey();
				if (key.startsWith(FILTER_KEY_NAME)) {
					String name = key.substring(FILTER_KEY_NAME.length());
					aliasMap.put(name, (String) entry.getValue());
				}
			}
		} catch (Exception e) {
		}
	}

	private Class<?> loadClass(String className) {
		Class<?> clazz = null;

		if (className == null) {
			return null;
		}

		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			// skip
		}

		ClassLoader ctxClassLoader = Thread.currentThread().getContextClassLoader();
		if (ctxClassLoader != null) {
			try {
				clazz = ctxClassLoader.loadClass(className);
			} catch (ClassNotFoundException e) {
				// skip
			}
		}

		return clazz;
	}

	private Properties loadFilterConfig() throws IOException {
		Properties filterProperties = new Properties();

		loadFilterConfig(filterProperties, ClassLoader.getSystemClassLoader());
		loadFilterConfig(filterProperties, Thread.currentThread().getContextClassLoader());

		return filterProperties;
	}

	private void loadFilterConfig(Properties filterProperties, ClassLoader classLoader) throws IOException {
		if (classLoader == null) {
			return;
		}

		for (Enumeration<URL> e = classLoader.getResources(FILTER_PROPERTY_NAME); e.hasMoreElements(); ) {
			URL url = e.nextElement();

			Properties property = new Properties();

			InputStream is = null;
			try {
				is = url.openStream();
				property.load(is);
			} finally {
				if (is != null) {
					is.close();
				}
			}

			filterProperties.putAll(property);
		}
	}

	private List<JdbcFilter> loadFilterFromCache(String filterName) {
		List<JdbcFilter> result = cachedFilterMap.get(filterName);
		if (result != null) {
			return result;
		}

		String filterClassNames = aliasMap.get(filterName);

		if (StringUtils.isEmpty(filterClassNames)) {
			return null;
		}

		result = new ArrayList<JdbcFilter>();

		for (String filterClassName : filterClassNames.split(",")) {
			Class<?> filterClass = loadClass(filterClassName);
			if (filterClass == null) {
				continue;
			}

			JdbcFilter filter;
			try {
				filter = (JdbcFilter) filterClass.newInstance();
				filter.init();
				result.add(filter);
			} catch (Exception ignore) {
			}
		}

		cachedFilterMap.put(filterName, result);
		return result;
	}

	@Override
	public List<JdbcFilter> loadFilters(String strConfig) {
		List<JdbcFilter> result = new LinkedList<JdbcFilter>();
		if (strConfig != null) {
			for (String name : strConfig.trim().split(",")) {
				List<JdbcFilter> filters = loadFilterFromCache(name.trim());
				if (filters != null && filters.size() > 0) {
					result.addAll(filters);
				}
			}
		}

		Collections.sort(result, new Comparator<JdbcFilter>() {
			@Override
			public int compare(JdbcFilter o1, JdbcFilter o2) {
				int x = o1.getOrder();
				int y = o2.getOrder();
				return (x > y) ? -1 : ((x == y) ? 0 : 1);
			}
		});
		return result;
	}
}