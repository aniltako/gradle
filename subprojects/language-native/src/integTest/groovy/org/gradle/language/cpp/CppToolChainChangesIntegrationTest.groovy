/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.cpp

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.AvailableToolChains.InstalledToolChain
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.junit.Assume
import spock.lang.Unroll

import static org.gradle.language.cpp.AbstractCppInstalledToolChainIntegrationTest.worksWithCppPlugin

class CppToolChainChangesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        def app = new CppHelloWorldApp()

        buildFile << """    
            project(':library') {
                apply plugin: 'cpp-library'
                library {
                    publicHeaders.from('src/main/headers')
                }
            }
            project(':app') {
                apply plugin: 'cpp-executable'
                dependencies {
                    implementation project(':library')
                }
            }
        """
        settingsFile << """
            rootProject.name = 'test'
            include 'library', 'app'
        """
        app.mainSource.writeToDir(file("app/src/main"))
        app.libraryHeader.writeToDir(file("library/src/main"))
        app.commonHeader.writeToDir(file("library/src/main"))
        app.librarySources.each {
            it.writeToDir(file("library/src/main"))
        }
    }

    @Unroll
    def "recompiles binary when toolchain changes from #toolChainBefore to #toolChainAfter"() {
        buildFile.text = buildScriptForToolChains(toolChainBefore, toolChainAfter)

        when:
        run ':app:compileDebugCpp'

        then:
        executedAndNotSkipped ':app:compileDebugCpp'

        when:
        run ':app:compileDebugCpp', '-PuseAlternativeToolChain=true', "--info"

        then:
        executedAndNotSkipped ':app:compileDebugCpp'
        output =~ /Value of input property 'compilerVersion\.(version|type)' has changed for task ':app:compileDebugCpp'/

        where:
        toolChains << toolChainPairs
        toolChainBefore = toolChains[0]
        toolChainAfter = toolChains[1]
    }

    @Unroll
    def "detects changes to system headers when toolchain changes from #toolChainBefore to #toolChainAfter"() {
        buildFile.text = buildScriptForToolChains(toolChainBefore, toolChainAfter)

        when:
        run ':app:compileDebugCpp'

        then:
        executedAndNotSkipped ':app:compileDebugCpp'

        when:
        run ':app:compileDebugCpp', '-PuseAlternativeToolChain=true', "--info"

        then:
        executedAndNotSkipped ':app:dependDebugCpp'
        output =~ /Value of input property 'includePaths' has changed for task ':app:dependDebugCpp'/

        where:
        toolChains << toolChainPairs
        toolChainBefore = toolChains[0]
        toolChainAfter = toolChains[1]
    }

    private static GString buildScriptForToolChains(InstalledToolChain before, InstalledToolChain after) {
        """ 
            allprojects {
                apply plugin: ${before.pluginClass}
                apply plugin: ${after.pluginClass}
                
                model {
                    toolChains {
                        if (findProperty('useAlternativeToolChain')) {
                            ${after.buildScriptConfig}
                        } else {
                            ${before.buildScriptConfig}
                        }
                    }
                }                                    
            }
            project(':library') {
                apply plugin: 'cpp-library'
                library {
                    publicHeaders.from('src/main/headers')
                }
            }
            project(':app') {
                apply plugin: 'cpp-executable'
                dependencies {
                    implementation project(':library')
                }
            }
        """
    }

    private static List<List<InstalledToolChain>> getToolChainPairs() {
        def availableToolChains = AvailableToolChains.toolChains.findAll {
            it.available && worksWithCppPlugin(it) && !(it instanceof AvailableToolChains.InstalledSwiftc)
        }
        int numberOfToolChains = availableToolChains.size()
        Assume.assumeTrue('2 or more tool chains are required for this test', numberOfToolChains >= 2)
        (0..<(numberOfToolChains - 1)).collectMany { first ->
            ((first+1)..<numberOfToolChains).collect { second ->
                [availableToolChains[first], availableToolChains[second]]
            }
        }
    }

}
