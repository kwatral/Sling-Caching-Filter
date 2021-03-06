## Introduction

Cache bundle is a OSGi bundle that provides caching mechanisms for pages or components. Cache bundle uses for it cache filter.

## Installation

Add following dependency to your project:

    <dependency>
        <groupId>com.cognifide.cq</groupId>
        <artifactId>sling-caching-filter</artifactId>
        <version>0.9.0-SNAPSHOT</version>
    </dependency>

or download the sources and use the `maven-sling-plugin`:

    mvn clean package sling:install

## Cache filter

### General description

Cache filter is able to cache all pages/renderers/components. It is configured to filter all sling requests on the component level. The only limit is that the whole component is being cached - to cache only a part of a component please use the cache tag.

It is strongly discouraged to enable cache filter on the author instance since this produces issues with CQ js code.

### Configuration

Cache filter can be configured in two places: in the OSGi console (filter configuration and components configurations). Under the hood [jcache](https://jcp.org/en/jsr/detail?id=107)  with [ehcache](https://github.com/ehcache/ehcache-jcache) are used.

#### OSGi console - Sling Caching Filter

OSGi console allows to modify the following properties:

* Enabled - enables/disables cache filter
* Validity itme - Maximum default time (in seconds) after which cache entry must be refreshed
* Path aliases - define aliases for paths, syntax `$<alias name>|<path 1>|<path 2>|...`, where `$` is a mandatory character before alias name, and `|` is a separator between paths
* Max entries in cache - defines max entries in cache, disc and heap. `0` means unlimited.
* Eviction policy - sets eviction policy, possible values: `LRU, LFU, FIFO`.

#### OSGi console - Sling Caching Filter Resource Type Definition

OSGi console allows to add configuration per component/resource type.

| attribute name                   | attribute type | required | description | default value |
| -------------------------------  | -------------- | -------- | ----------- | ------------- |
| Activate (cache.config.active) | boolean | no | enables/disables caching of given component | false |
| Resource type (cache.config.resource.type) | String | yes | component resource type | -1 |
| Validity time (cache.config.validity.time) | integer | no | specifies cache entry validity time (in seconds) | duration property read from the OSGi console |
| Cache level (cache.config.cache.level) | String | no | specifies the level of component caching | -1 |
| Invalidate on self (cache.config.invalidate.on.self) | boolean | no | when set to true cached instance will be refreshed if it has been changed | true |
| Invalidate on containing page (cache.config.invalidate.on.containing.page) | boolean | no | when set to true cached instance will be refreshed when something will change on page containing cached instance | false |
| Invalidate on referenced fields (cache.config.invalidate.on.referenced.fields) | String[] | no       | List of component fields that store links to content/configuration/etc. pages. Links from those fields are loaded and each content change inside nodes pointed to by those links will invalidate cache of the current component | empty list |
| Invalidate on paths (cache.config.invalidate.on.paths) | String[] | no | List of paths (regular expressions). If a path of any changed JCR node matches any path from the list then the cache of the current component is invalidated | empty list |

Allowed values for the `Cache level`:

* -1 - Each instance is cached separately (resource path is used to create cache key).
* 0 - There is only one instance of the component on the whole site. To determine which instance is cached, the first-renderer rule applies (the first rednered component is cached and used on other pages)
* any positive value - Component is cached per path. The value of cache level determines how many parts of the request URI (separated by the "/" character) will be used to generate cache key. For example, when this value is set to 3 and the path is /content/acme/en_gb/home.html, then only "/content/acme/en_gb" will be used to generate the key meaning that component will be cached per language.

## TODOs

Things/ideas left out for the future:
* implement (if ever required) different caching strategies for different selectors
* add configuration of paths that are (or are not) filtered by the cache filter
