package skunk

import cats._
import cats.implicits._
import fs2.Chunk
import fs2.io.tcp.Socket
import scala.concurrent.duration.FiniteDuration
import scodec.bits.BitVector
import scodec.codecs._

/** A higher-level socket interface defined in terms of `BitVector`. */
trait BitVectorSocket[F[_]] {
  def write(bits: BitVector): F[Unit]
  def readBytes(n: Int): F[Array[Byte]]
  def readBitVector(n: Int): F[BitVector]
  def readInt16: F[Int]
  def readInt32: F[Int]
  def readChar: F[Char]
}

object BitVectorSocket {

  def fromSocket[F[_]](socket: Socket[F], readTimeout: FiniteDuration, writeTimeout: FiniteDuration)(
    implicit ev: MonadError[F, Throwable]
  ): BitVectorSocket[F] =
    new BitVectorSocket[F] {

      def readBytes(n: Int): F[Array[Byte]] =
        socket.readN(n, Some(readTimeout)).flatMap {
          case None => ev.raiseError(new Exception("Fatal: EOF"))
          case Some(c) =>
            if (c.size == n) ev.pure(c.toArray)
            else ev.raiseError(new Exception(s"Fatal: EOF before $n bytes could be read.Bytes"))
        }

      def readBitVector(n: Int): F[BitVector] =
        readBytes(n).map(BitVector(_))

      def readInt16: F[Int] =
        readBitVector(2).map(int16.decodeValue(_).require)

      def readInt32: F[Int] =
        readBitVector(4).map(int32.decodeValue(_).require)

      def readChar: F[Char] =
        readBitVector(1).map(int8.decodeValue(_).require.toChar)

      def write(bits: BitVector): F[Unit] =
        socket.write(Chunk.array(bits.toByteArray), Some(writeTimeout))

    }

}
