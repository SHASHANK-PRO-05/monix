/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
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

package monix.streams.internal.operators

import monix.execution.Ack.Continue
import monix.streams.exceptions.DummyException
import monix.streams.{Observable, Observer}
import scala.concurrent.duration.Duration.Zero
import scala.concurrent.{Future, Promise}

object FilterSuite extends BaseOperatorSuite {
  def count(sourceCount: Int) = {
    sourceCount
  }

  def sum(sourceCount: Int): Long =
    sourceCount.toLong * (sourceCount + 1)

  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = if (sourceCount == 1)
        Observable.now(2L).filter(_ % 2 == 0)
      else
        Observable.range(1, sourceCount * 2 + 1, 1).filter(_ % 2 == 0)

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val ex = DummyException("dummy")
      val o = if (sourceCount == 1)
        createObservableEndingInError(Observable.now(2L), ex)
          .filter(_ % 2 == 0)
      else
        createObservableEndingInError(Observable.range(1, sourceCount * 2 + 1, 1), ex)
          .filter(_ % 2 == 0)

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = if (sourceCount == 1)
        Observable.now(1L).filter(_ => throw ex)
      else
        Observable.range(1, sourceCount * 2 + 1, 1).filter { x =>
          if (x == sourceCount * 2)
            throw ex
          else
            x % 2 == 0
        }

      Sample(o, count(sourceCount-1), sum(sourceCount-1), Zero, Zero)
    }
  }

  test("should not do back-pressure for onComplete, for 1 element") { implicit s =>
    val p = Promise[Continue]()
    var wasCompleted = false

    createObservable(1) match {
      case ref @ Some(Sample(obs, count, sum, waitForFirst, waitForNext)) =>
        var onNextReceived = false

        obs.unsafeSubscribeFn(new Observer[Long] {
          def onNext(elem: Long): Future[Continue] = { onNextReceived = true; p.future }
          def onError(ex: Throwable): Unit = throw new IllegalStateException()
          def onComplete(): Unit = wasCompleted = true
        })

        s.tick(waitForFirst)
        assert(onNextReceived)
        assert(wasCompleted)
        p.success(Continue)
        s.tick(waitForNext)
    }
  }
}
