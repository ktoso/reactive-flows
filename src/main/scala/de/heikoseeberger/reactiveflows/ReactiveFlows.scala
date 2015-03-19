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

import akka.actor.{ Actor, ActorLogging, ActorPath, ActorRef, Address, Props, RootActorPath, SupervisorStrategy, Terminated }
import akka.contrib.datareplication.DataReplication
import akka.contrib.pattern.DistributedPubSubExtension
import akka.persistence.journal.leveldb.{ SharedLeveldbJournal, SharedLeveldbStore }
import java.nio.file.{ Paths, Files }

object ReactiveFlows {

  final val Name = "reactive-flows"

  final val SharedJournal = "shared-journal"

  def props(runSharedJournal: Boolean) = Props(new ReactiveFlows(runSharedJournal))

  def sharedJournal(address: Address): ActorPath = RootActorPath(address) / "user" / Name / SharedJournal
}

class ReactiveFlows(runSharedJournal: Boolean) extends Actor with ActorLogging with SettingsActor {

  import ReactiveFlows._

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  private val flowFacade = context.watch(createFlowFacade())

  if (runSharedJournal) {
    deleteDir(Paths.get(
      context.system.settings.config.getString("akka.persistence.journal.leveldb-shared.store.dir")
    ))
    val sharedJournal = context.watch(context.actorOf(Props(new SharedLeveldbStore), SharedJournal))
    SharedLeveldbJournal.setStore(sharedJournal, context.system)
  }
  context.watch(context.actorOf(SharedJournalSetter.props, SharedJournalSetter.Name))
  context.watch(createHttpService(flowFacade))

  override def receive = {
    case Terminated(actor) => shutdown(actor)
  }

  protected def createFlowFacade(): ActorRef = context.actorOf(
    FlowFacade.props(DistributedPubSubExtension(context.system).mediator, DataReplication(context.system).replicator),
    FlowFacade.Name
  )

  protected def createHttpService(flowFacade: ActorRef): ActorRef = context.actorOf(
    HttpService.props(
      settings.httpService.interface,
      settings.httpService.port,
      settings.httpService.selfTimeout,
      flowFacade,
      settings.httpService.flowFacadeTimeout,
      DistributedPubSubExtension(context.system).mediator
    ),
    HttpService.Name
  )

  protected def shutdown(actor: ActorRef): Unit = {
    log.error("Shutting down the system because {} terminated!", actor)
    context.system.shutdown()
  }
}
