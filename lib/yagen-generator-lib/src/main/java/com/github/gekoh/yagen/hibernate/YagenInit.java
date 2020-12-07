/*
 Copyright 2014 Georg Kohlweiss

 Licensed under the Apache License, Version 2.0 (the License);
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an AS IS BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
package com.github.gekoh.yagen.hibernate;

/**
 * @author Georg Kohlweiss
 */
public class YagenInit {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(YagenInit.class);

    /**
     * Initialize yagen which means trying to patch all required hibernate classes<br>
     *
     * @throws Exception if we cannot patch the hibernate classes
     */
    public static void init() throws Exception {
        PatchHibernateMappingClasses.applyPatch();
    }

    public static void patch4Transfer() {
        try {
            init();
            ReflectExecutor.m_patchCollectionsAlwaysLazy.get().invoke(null);
            ReflectExecutor.m_patchIgnoreVersion.get().invoke(null);
        } catch (Exception e) {
            LOG.error("unable to patch for transfer", e);
        }
    }

}