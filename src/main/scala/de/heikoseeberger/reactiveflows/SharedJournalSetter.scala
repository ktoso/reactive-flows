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

import akka.actor.{ Terminated, Actor, ActorIdentity, ActorLogging, ActorRef, Identify, Props, ReceiveTimeout }
import akka.cluster.ClusterEvent.{ InitialStateAsEvents, MemberUp }
import akka.cluster.{ Cluster, Member }
import akka.persistence.journal.leveldb.SharedLeveldbJournal
import scala.concurrent.duration.{ Duration, DurationInt }

object SharedJournalSetter {

  val Name = "shared-journal-setter"

  def props: Props = Props(new SharedJournalSetter)
}

class SharedJournalSetter extends Actor with ActorLogging {

  override def preStart() = Cluster(context.system).subscribe(self, InitialStateAsEvents, classOf[MemberUp])

  override def receive = waiting

  private def waiting: Receive = {
    case MemberUp(member) if member.hasRole(ReactiveFlows.SharedJournal) => onSharedJournalMemberUp(member)
  }

  private def becomeIdentifying() = {
    context.setReceiveTimeout(10 seconds)
    context.become(identifying)
  }

  private def identifying: Receive = {
    case ActorIdentity(_, Some(sharedJournal)) =>
      SharedLeveldbJournal.setStore(sharedJournal, context.system)
      log.debug("Succssfully set shared journal {}", sharedJournal)
      becomeWatching(sharedJournal)
    case ActorIdentity(_, None) =>
      log.error("Can't identify shared journal!")
      context.stop(self)
    case ReceiveTimeout =>
      log.error("Timeout identifying shared journal!")
      context.stop(self)
  }

  private def becomeWatching(sharedJournal: ActorRef) = {
    context.watch(sharedJournal)
    context.setReceiveTimeout(Duration.Undefined)
    context.become(watching)
  }

  private def watching: Receive = {
    case Terminated(_) =>
      log.error("Shared journal terminated!")
      context.stop(self)
  }

  private def onSharedJournalMemberUp(member: Member) = {
    val sharedJournal = context.actorSelection(ReactiveFlows.sharedJournal(member.address))
    sharedJournal ! Identify(None)
    becomeIdentifying()
  }
}
