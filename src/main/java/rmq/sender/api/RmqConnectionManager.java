/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rmq.sender.api;

/**
 * Interface for declaring a start, publisher, consumer and stop api's for rabbitmq communication.
 */
public interface RmqConnectionManager {
    /**
     * Establishes connection with RabbitMQ server.
     */
    void start();

    /**
     * Publisher usded by SDN agent to provide required information to outside applications by using RabbitMQ server.
     */
    void publisher();

    /**
     * Releases RabbitMQ server's connection and channels.
     */
    void stop();

    /**
     * Consumer recieve commnads from outside applications by using RabbitMQ server.
     */
    String consumer();


}
