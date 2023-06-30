package org.stellar.anchor.platform.action.handlers;

import static org.stellar.anchor.api.platform.PlatformTransactionData.Kind.DEPOSIT;
import static org.stellar.anchor.api.sep.SepTransactionStatus.INCOMPLETE;
import static org.stellar.anchor.api.sep.SepTransactionStatus.PENDING_ANCHOR;
import static org.stellar.anchor.api.sep.SepTransactionStatus.PENDING_USR_TRANSFER_START;
import static org.stellar.anchor.platform.action.dto.ActionMethod.REQUEST_OFFCHAIN_FUNDS;

import java.util.HashSet;
import java.util.Set;
import javax.validation.Validator;
import org.springframework.stereotype.Service;
import org.stellar.anchor.api.exception.BadRequestException;
import org.stellar.anchor.api.platform.PlatformTransactionData.Kind;
import org.stellar.anchor.api.sep.SepTransactionStatus;
import org.stellar.anchor.asset.AssetService;
import org.stellar.anchor.platform.action.dto.ActionMethod;
import org.stellar.anchor.platform.action.dto.RequestOffchainFundsRequest;
import org.stellar.anchor.platform.data.JdbcSep24Transaction;
import org.stellar.anchor.platform.data.JdbcSepTransaction;
import org.stellar.anchor.sep24.Sep24TransactionStore;
import org.stellar.anchor.sep31.Sep31TransactionStore;

@Service
public class RequestOffchainFundsHandler extends ActionHandler<RequestOffchainFundsRequest> {

  public RequestOffchainFundsHandler(
      Sep24TransactionStore txn24Store,
      Sep31TransactionStore txn31Store,
      Validator validator,
      AssetService assetService) {
    super(txn24Store, txn31Store, validator, assetService);
  }

  @Override
  public ActionMethod getActionType() {
    return REQUEST_OFFCHAIN_FUNDS;
  }

  @Override
  protected SepTransactionStatus getNextStatus(
      JdbcSepTransaction txn, RequestOffchainFundsRequest request) {
    return PENDING_USR_TRANSFER_START;
  }

  @Override
  protected Set<SepTransactionStatus> getSupportedStatuses(JdbcSepTransaction txn) {
    Set<SepTransactionStatus> supportedStatuses = new HashSet<>();
    JdbcSep24Transaction txn24 = (JdbcSep24Transaction) txn;
    if (DEPOSIT == Kind.from(txn24.getKind())) {
      supportedStatuses.add(INCOMPLETE);
      if (txn24.getTransferReceivedAt() == null) {
        supportedStatuses.add(PENDING_ANCHOR);
      }
    }
    return supportedStatuses;
  }

  @Override
  protected Set<String> getSupportedProtocols() {
    return Set.of("24");
  }

  @Override
  protected void updateTransactionWithAction(
      JdbcSepTransaction txn, RequestOffchainFundsRequest request) throws BadRequestException {
    validateAsset("amount_in", request.getAmountIn());
    validateAsset("amount_out", request.getAmountOut());
    validateAsset("amount_fee", request.getAmountFee(), true);
    validateAsset("amount_expected", request.getAmountOut());

    if (request.getAmountIn() != null) {
      txn.setAmountIn(request.getAmountIn().getAmount());
      txn.setAmountInAsset(request.getAmountIn().getAsset());
    } else if (txn.getAmountIn() == null) {
      throw new BadRequestException("amount_in is required");
    }

    if (request.getAmountOut() != null) {
      txn.setAmountIn(request.getAmountOut().getAmount());
      txn.setAmountInAsset(request.getAmountOut().getAsset());
    } else if (txn.getAmountOut() == null) {
      throw new BadRequestException("amount_out is required");
    }

    if (request.getAmountFee() != null) {
      txn.setAmountIn(request.getAmountFee().getAmount());
      txn.setAmountInAsset(request.getAmountFee().getAsset());
    } else if (txn.getAmountFee() == null) {
      throw new BadRequestException("amount_fee is required");
    }

    JdbcSep24Transaction txn24 = (JdbcSep24Transaction) txn;
    if (request.getAmountExpected() != null) {
      txn24.setAmountExpected(request.getAmountExpected().getAmount());
    } else {
      txn24.setAmountExpected(txn.getAmountIn());
    }
  }
}
