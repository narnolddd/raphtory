package com.raphtory.core.actors.analysismanager

import scala.concurrent.duration._

import akka.actor.Cancellable
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}

import scala.concurrent.ExecutionContext.Implicits.global
import com.raphtory.core.actors.RaphtoryActor
import com.raphtory.core.model.communication._
import com.raphtory.core.utils.Utils
import com.raphtory.core.analysis.Analyser
import com.raphtory.core.storage.controller.GraphRepoProxy

abstract class LiveAnalyser extends RaphtoryActor {
  private var managerCount : Int = 0
  private var currentStep  = 0L
  private var networkSize  = 0
  private var networkSizeReplies = 0
  private var networkSizeTimeout : Cancellable = null
  private var readyCounter       = 0
  private var currentStepCounter = 0
  private var toSetup            = true

  protected val mediator     = DistributedPubSub(context.system).mediator
  protected var steps  : Long= 0L
  protected var results      = Vector.empty[Any]
  protected var oldResults      = Vector.empty[Any]

  implicit val proxy : GraphRepoProxy.type = null

  protected def defineMaxSteps() : Unit
  protected def generateAnalyzer : Analyser
  protected def processResults(result : Any) : Unit
  protected def processOtherMessages(value : Any) : Unit
  protected def checkProcessEnd() : Boolean = false

  mediator ! DistributedPubSubMediator.Put(self)
  mediator ! DistributedPubSubMediator.Subscribe(Utils.partitionsTopic, self)
  mediator ! DistributedPubSubMediator.Subscribe(Utils.liveAnalysisTopic, self)

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(Duration(5, MINUTES), self, "start")
    //context.system.scheduler.schedule(Duration(5, MINUTES), Duration(10, MINUTES), self, "start") // Refresh networkSize and restart analysis currently
  }

  protected final def getNetworkSize  : Int = networkSize
  protected final def getManagerCount : Int = managerCount
  protected final def sendGetNetworkSize() : Unit = {
    networkSize        = 0
    networkSizeReplies = 0
    networkSizeTimeout = context.system.scheduler.scheduleOnce(Duration(30, SECONDS), self, "networkSizeTimeout")
    mediator ! DistributedPubSubMediator.Publish(Utils.readersTopic, GetNetworkSize())
  }

  override def receive: Receive = {
    //case "analyse" => analyse()
    case "start" => {
      println("Starting")
      sendGetNetworkSize()
    }
    case "networkSizeTimeout" => {
      sendGetNetworkSize()
      println("Timeout networkSize")
    }
    case Results(result) => {
      println("Printing results")
      this.processResults(result)
    }
    case PartitionsCount(newValue) => {
      managerCount = newValue
    }
    case NetworkSize(size) => {
        println("Received NetworkSize packet")
        networkSize += size
        networkSizeReplies += 1
        if (networkSizeReplies >= getManagerCount) {
          networkSizeTimeout.cancel()
          println(steps)
          this.defineMaxSteps()
          println(networkSize)
          mediator ! DistributedPubSubMediator.Publish(Utils.readersTopic, Setup(this.generateAnalyzer))
        }
      }
    case Ready() => {
      println("Received ready")
      readyCounter += 1
      println(s"$readyCounter / ${getManagerCount}")
      if (readyCounter == getManagerCount) {
        readyCounter = 0
        currentStep = 1
        results = Vector.empty[Any]
        println(s"Sending analyzer")
        mediator ! DistributedPubSubMediator.Publish(Utils.readersTopic, NextStep(this.generateAnalyzer))
      }
    }
    case EndStep(res) => {
      println("EndStep")
      currentStepCounter += 1
      results +:= res
      println(s"$currentStepCounter / $getManagerCount : $currentStep / $steps")
      if (currentStepCounter >= getManagerCount) {
        if (currentStep == steps || this.checkProcessEnd()) {
          // Process results
          this.processResults(results)
          context.system.scheduler.scheduleOnce(Duration(5, MINUTES), self, "start")
        } else {
          println(s"Sending new step")
          oldResults = results
          results = Vector.empty[Any]
          currentStep += 1
          currentStepCounter = 0
          mediator ! DistributedPubSubMediator.Publish(Utils.readersTopic, NextStep(this.generateAnalyzer))
        }
      }
    }
    case _ => processOtherMessages(_)
  }

  //  private final def analyse() ={
  //      for(i <- 0 until managerCount){
  //        mediator ! DistributedPubSubMediator.Send(s"/user/Manager_$i",
  //          LiveAnalysis(
  //            Class.forName(sys.env.getOrElse("ANALYSISTASK", classOf[GabPageRank].getClass.getName))
  //              .newInstance().asInstanceOf[Analyser]),
  //          false)
  //      }
  //  }
}
