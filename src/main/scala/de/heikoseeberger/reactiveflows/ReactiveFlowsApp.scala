/*
 * Copyright 2015 Heiko Seeberger
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

package de.heikoseeberger.reactiveflows

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.contrib.pattern.DistributedPubSubExtension
import akka.event.Logging
import com.typesafe.conductr.bundlelib.akka.Env
import com.typesafe.config.ConfigFactory

object ReactiveFlowsApp {

  private val jvmArg = """-D(\S+)=(\S+)""".r

  def main(args: Array[String]): Unit = {
    for (jvmArg(name, value) <- args) System.setProperty(name, value)

    val name = sys.env.getOrElse("BUNDLE_SYSTEM", "reactive-flows-system")
    val config = Env.asConfig.withFallback(ConfigFactory.load())
    val system = ActorSystem(name, config)
    Cluster(system).registerOnMemberUp {
      FlowFacade.startSharding(
        system,
        DistributedPubSubExtension(system).mediator,
        Settings(system).flowFacade.shardCount
      )
      system.actorOf(ReactiveFlows.props, ReactiveFlows.Name)
      Logging(system, getClass).info("Reactive Flows up and running")
    }

    system.awaitTermination()
  }
}
