/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.launcher.daemon.bootstrap

import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DaemonOutputConsumerTest extends ConcurrentSpec {
    def consumer = new DaemonOutputConsumer(new ByteArrayInputStream([] as byte[]))

    def "input process and name cannot be null"() {
        when:
        consumer.connectStreams((Process) null, "foo", executorFactory)
        then:
        thrown(IllegalArgumentException)

        when:
        consumer.connectStreams(Mock(Process), null, executorFactory)
        then:
        thrown(IllegalArgumentException)
    }

    def "forwards process input"() {
        def consumer = new DaemonOutputConsumer(new ByteArrayInputStream("send this to the process".bytes))
        def receivedInput = new ByteArrayOutputStream()
        def process = process("", receivedInput)

        when:
        consumer.connectStreams(process , "cool process", executorFactory)
        consumer.start()
        consumer.stop()

        then:
        receivedInput.toString() == "send this to the process"
    }

    def "consumes process output until EOF"() {
        def process = process('hey Joe!')

        when:
        consumer.connectStreams(process , "cool process", executorFactory)
        consumer.start()
        consumer.stop()
        then:
        consumer.processOutput.trim() == 'hey Joe!'
    }

    def "consumes process greeting noticed in output"() {
        def output = """
           Hey!
           Come visit Krakow
           It's nice
           !!!
        """
        def process = process(output)

        given:
        consumer.startupCommunication = Mock(DaemonStartupCommunication)
        consumer.startupCommunication.containsGreeting( {it.contains "Come visit Krakow"} ) >> true

        when:
        consumer.connectStreams(process , "cool process", executorFactory)
        consumer.start()
        consumer.stop()

        then:
        consumer.processOutput.trim().endsWith("Come visit Krakow")
    }

    def "connecting streams is required initially"() {
        expect:
        illegalStateReportedWhen {consumer.start()}
        illegalStateReportedWhen {consumer.stop()}
        illegalStateReportedWhen {consumer.processOutput}
    }

    def "starting is required"() {
        when:
        consumer.connectStreams(process(""), "cool process", executorFactory)

        then:
        illegalStateReportedWhen {consumer.stop()}
        illegalStateReportedWhen {consumer.processOutput}
    }

    def "stopping is required"() {
        when:
        consumer.connectStreams(process("") , "cool process", executorFactory)
        consumer.start()

        then:
        illegalStateReportedWhen {consumer.processOutput}
    }

    void illegalStateReportedWhen(Closure action) {
        try {
            action()
            assert false
        } catch (IllegalStateException) {}
    }

    def process(String input = "", OutputStream processInput = new ByteArrayOutputStream()) {
        return Stub(Process) {
            getInputStream() >> new ByteArrayInputStream(input.bytes)
            getOutputStream() >> processInput
        }
    }
}
