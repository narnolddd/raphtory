package com.raphtory.examples.GenericAlgorithms.ConnectedComponents

import com.raphtory.core.analysis.{Analyser, WorkerID}
import com.raphtory.core.model.communication.VertexMessage

import scala.collection.mutable

class ConComAnalyser extends Analyser {

  case class ClusterLabel(value: Int) extends VertexMessage

  override def setup()(implicit workerID: WorkerID) = {
    proxy.getVerticesSet().foreach(v => {
      val vertex = proxy.getVertex(v)
      var min = v
      //math.min(v, vertex.getOutgoingNeighbors.union(vertex.getIngoingNeighbors).min)
      val toSend = vertex.getOrSetCompValue("cclabel", min).asInstanceOf[Int]
      vertex.messageAllNeighbours(ClusterLabel(toSend))
    })
  }

  override def analyse()(implicit workerID: WorkerID): Any= {
    var results = mutable.HashMap[Int, Int]()
    proxy.getVerticesSet().foreach(v => {
      val vertex = proxy.getVertex(v)
      val queue = vertex.messageQueue.map(_.asInstanceOf[ClusterLabel].value)
      var label = v
      if(queue.nonEmpty)
        label = queue.min
      vertex.messageQueue.clear
      //while (vertex moreMessages)
       // label = math.min(label, vertex.nextMessage().asInstanceOf[ClusterLabel].value)
      var currentLabel = vertex.getOrSetCompValue("cclabel",v).asInstanceOf[Int]
      if (label < currentLabel) {
        vertex.setCompValue("cclabel", label)
        vertex messageAllOutgoingNeighbors (ClusterLabel(label))
        currentLabel = label
      }
      else{
        //vertex.voteToHalt()
      }
      results.get(currentLabel) match {
        case Some(currentCount) => results(currentLabel) = currentCount +1
        case None => results(currentLabel) = 1
      }
    })
    //println(s"analyse $results")
    results
  }


}
