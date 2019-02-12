package com.wavesplatform.transaction.protobuf

import java.nio.charset.StandardCharsets

import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.transaction.protobuf.Transaction.Data
import com.wavesplatform.transaction.protobuf.Transaction.Data.MassTransfer
import com.wavesplatform.transaction.transfer.MassTransferTransaction
import com.wavesplatform.transaction.transfer.MassTransferTransaction.ParsedTransfer
import com.wavesplatform.transaction.{AssetId, Transaction => VanillaTransaction}
import com.wavesplatform.{transaction => vt}

trait PBTransactionImplicits {
  private[this] val WavesAssetId = ByteStr.empty
  private[this] val FeeAssetId   = WavesAssetId
  private[this] lazy val NoChainId = 0 // AddressScheme.current.chainId

  implicit class VanillaOrderImplicitConversionOps(order: vt.assets.exchange.Order) {
    def toPB: ExchangeTransactionData.Order = {
      ExchangeTransactionData.Order(
        order.senderPublicKey,
        order.matcherPublicKey,
        Some(ExchangeTransactionData.Order.AssetPair(order.assetPair.amountAsset, order.assetPair.priceAsset)),
        order.orderType match {
          case vt.assets.exchange.OrderType.BUY  => ExchangeTransactionData.Order.Type.BUY
          case vt.assets.exchange.OrderType.SELL => ExchangeTransactionData.Order.Type.SELL
        },
        order.amount,
        order.price,
        order.timestamp,
        order.expiration,
        order.matcherFee,
        order.proofs.proofs,
        order.version
      )
    }
  }

  implicit class PBOrderImplicitConversionOps(order: ExchangeTransactionData.Order) {
    def toVanilla: vt.assets.exchange.Order = {
      vt.assets.exchange.Order(
        order.senderPublicKey,
        order.matcherPublicKey,
        vt.assets.exchange.AssetPair(order.getAssetPair.amountAssetId, order.getAssetPair.priceAssetId),
        order.orderType match {
          case ExchangeTransactionData.Order.Type.BUY  => vt.assets.exchange.OrderType.BUY
          case ExchangeTransactionData.Order.Type.SELL => vt.assets.exchange.OrderType.SELL
        },
        order.amount,
        order.price,
        order.timestamp,
        order.expiration,
        order.matcherFee,
        vt.Proofs(order.proofs),
        order.version.toByte
      )
    }
  }

  implicit class VanillaTransactionImplicitConversionOps(tx: VanillaTransaction) {
    def toPB: Transaction = tx match {
      // Uses version "2" for "modern" transactions with single version and proofs field
      case MassTransferTransaction(assetId, sender, transfers, timestamp, fee, attachment, proofs) =>
        val data = MassTransferTransactionData(transfers.map(pt => MassTransferTransactionData.Transfer(pt.address, pt.amount)))
        Transaction(assetId, sender, NoChainId, fee, FeeAssetId, ByteStr(attachment), timestamp, 2, proofs.proofs, Data.MassTransfer(data))

      case vt.transfer.TransferTransactionV1(assetId, sender, recipient, amount, timestamp, feeAssetId, fee, attachment, signature) =>
        val data = TransferTransactionData(recipient, amount)
        Transaction(assetId, sender, NoChainId, fee, feeAssetId, ByteStr(attachment), timestamp, 1, Seq(signature), Data.Transfer(data))

      case vt.transfer.TransferTransactionV2(sender, recipient, assetId, amount, timestamp, feeAssetId, fee, attachment, proofs) =>
        val data = TransferTransactionData(recipient, amount)
        Transaction(assetId, sender, NoChainId, fee, feeAssetId, ByteStr(attachment), timestamp, 2, proofs.proofs, Data.Transfer(data))

      case tx @ vt.CreateAliasTransactionV1(sender, alias, fee, timestamp, signature) =>
        val data = CreateAliasTransactionData(alias)
        Transaction(None, sender, NoChainId, fee, tx.assetFee._1, None, timestamp, 1, Seq(signature), Data.CreateAlias(data))

      case tx @ vt.CreateAliasTransactionV2(sender, alias, fee, timestamp, proofs) =>
        val data = CreateAliasTransactionData(alias)
        Transaction(None, sender, NoChainId, fee, tx.assetFee._1, None, timestamp, 2, proofs.proofs, Data.CreateAlias(data))

      case tx @ vt.assets.exchange
            .ExchangeTransactionV1(buyOrder, sellOrder, amount, price, buyMatcherFee, sellMatcherFee, fee, timestamp, signature) =>
        val data = ExchangeTransactionData(Some(buyOrder.toPB), Some(sellOrder.toPB), amount, price, buyMatcherFee, sellMatcherFee)
        Transaction(None, tx.sender, NoChainId, fee, tx.assetFee._1, None, timestamp, 1, Seq(signature), Data.Exchange(data))

      case tx @ vt.assets.exchange.ExchangeTransactionV2(buyOrder, sellOrder, amount, price, buyMatcherFee, sellMatcherFee, fee, timestamp, proofs) =>
        val data = ExchangeTransactionData(Some(buyOrder.toPB), Some(sellOrder.toPB), amount, price, buyMatcherFee, sellMatcherFee)
        Transaction(None, tx.sender, NoChainId, fee, tx.assetFee._1, None, timestamp, 2, proofs.proofs, Data.Exchange(data))

      case vt.assets.IssueTransactionV1(sender, name, description, quantity, decimals, reissuable, fee, timestamp, signature) =>
        val data = IssueTransactionData(new String(name, StandardCharsets.UTF_8),
                                        new String(description, StandardCharsets.UTF_8),
                                        quantity,
                                        decimals,
                                        reissuable,
                                        None)
        Transaction(None, sender, NoChainId, fee, tx.assetFee._1, None, timestamp, 1, Seq(signature), Data.Issue(data))

      case vt.assets.IssueTransactionV2(chainId, sender, name, description, quantity, decimals, reissuable, script, fee, timestamp, proofs) =>
        val data = IssueTransactionData(new String(name, StandardCharsets.UTF_8),
                                        new String(description, StandardCharsets.UTF_8),
                                        quantity,
                                        decimals,
                                        reissuable,
                                        script)
        Transaction(None, sender, chainId, fee, tx.assetFee._1, None, timestamp, 2, proofs.proofs, Data.Issue(data))

      case tx @ vt.assets.ReissueTransactionV1(sender, assetId, quantity, reissuable, fee, timestamp, signature) =>
        val data = ReissueTransactionData(quantity, reissuable)
        Transaction(assetId, sender, tx.chainByte.getOrElse(NoChainId), fee, tx.assetFee._1, None, timestamp, 1, Seq(signature), Data.Reissue(data))

      case tx @ vt.assets.ReissueTransactionV2(chainId, sender, assetId, quantity, reissuable, fee, timestamp, proofs) =>
        val data = ReissueTransactionData(quantity, reissuable)
        Transaction(assetId, sender, chainId, fee, tx.assetFee._1, None, timestamp, 2, proofs.proofs, Data.Reissue(data))

      case tx @ vt.assets.BurnTransactionV1(sender, assetId, quantity, fee, timestamp, signature) =>
        val data = BurnTransactionData(quantity)
        Transaction(assetId, sender, tx.chainByte.getOrElse(NoChainId), fee, tx.assetFee._1, None, timestamp, 1, Seq(signature), Data.Burn(data))

      case tx @ vt.assets.BurnTransactionV2(chainId, sender, assetId, quantity, fee, timestamp, proofs) =>
        val data = BurnTransactionData(quantity)
        Transaction(assetId, sender, chainId, fee, tx.assetFee._1, None, timestamp, 2, proofs.proofs, Data.Burn(data))

      case vt.assets.SetAssetScriptTransaction(chainId, sender, assetId, script, fee, timestamp, proofs) =>
        val data = SetAssetScriptTransactionData(script)
        Transaction(assetId, sender, chainId, fee, tx.assetFee._1, None, timestamp, 2, proofs.proofs, Data.SetAssetScript(data))

      case vt.smart.SetScriptTransaction(chainId, sender, script, fee, timestamp, proofs) =>
        val data = SetScriptTransactionData(script)
        Transaction(None, sender, chainId, fee, tx.assetFee._1, None, timestamp, 2, proofs.proofs, Data.SetScript(data))

      case tx @ vt.lease.LeaseTransactionV1(sender, amount, fee, timestamp, recipient, signature) =>
        val data = LeaseTransactionData(recipient, amount)
        Transaction(None, sender, NoChainId, fee, tx.assetFee._1, None, timestamp, 1, Seq(signature), Data.Lease(data))

      case tx @ vt.lease.LeaseTransactionV2(sender, amount, fee, timestamp, recipient, proofs) =>
        val data = LeaseTransactionData(recipient, amount)
        Transaction(None, sender, NoChainId, fee, tx.assetFee._1, None, timestamp, 2, proofs.proofs, Data.Lease(data))

      case tx @ vt.lease.LeaseCancelTransactionV1(sender, leaseId, fee, timestamp, signature) =>
        val data = LeaseCancelTransactionData(leaseId)
        Transaction(None, sender, NoChainId, fee, tx.assetFee._1, None, timestamp, 1, Seq(signature), Data.LeaseCancel(data))

      case tx @ vt.lease.LeaseCancelTransactionV2(chainId, sender, leaseId, fee, timestamp, proofs) =>
        val data = LeaseCancelTransactionData(leaseId)
        Transaction(None, sender, chainId, fee, tx.assetFee._1, None, timestamp, 2, proofs.proofs, Data.LeaseCancel(data))

      case _ =>
        throw new IllegalArgumentException(s"Unsupported transaction: $tx")
    }
  }

  implicit class PBTransactionImplicitConversionOps(tx: Transaction) {
    def toVanilla: VanillaTransaction = tx.data match {
      case MassTransfer(MassTransferTransactionData(transfers)) =>
        MassTransferTransaction(
          tx.assetId,
          tx.sender,
          transfers.map(t => ParsedTransfer(t.address, t.amount)).toList,
          tx.timestamp,
          tx.fee,
          tx.attachment.arr,
          tx.proofs
        )

      case data =>
        throw new IllegalArgumentException(s"Unsupported transaction data: $data")
    }
  }

  private[this] implicit def implicitAssetIdToOption(assetId: AssetId): Option[AssetId] =
    Option(assetId).filterNot(_.isEmpty)

  private[this] implicit def implicitAssetIdOptionToAssetId(assetId: Option[AssetId]): AssetId =
    assetId.getOrElse(WavesAssetId)
}

object PBTransactionImplicits extends PBTransactionImplicits
