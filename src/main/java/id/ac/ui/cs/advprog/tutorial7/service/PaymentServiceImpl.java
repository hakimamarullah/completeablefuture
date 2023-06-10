package id.ac.ui.cs.advprog.tutorial7.service;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

import org.springframework.stereotype.Service;

import id.ac.ui.cs.advprog.tutorial7.core.bankapi.BankApi;
import id.ac.ui.cs.advprog.tutorial7.core.miscapi.HolidayApi;
import id.ac.ui.cs.advprog.tutorial7.core.vaapi.VAHelper;
import id.ac.ui.cs.advprog.tutorial7.core.vaapi.VirtualAccount;
import id.ac.ui.cs.advprog.tutorial7.model.PaymentResponse;

@Service
public class PaymentServiceImpl implements PaymentService{

    HolidayApi holidayApi = new HolidayApi();
    VAHelper vaHelper = new VAHelper();

    @Override
    public String createNewVA(int vaAmount, String bank) {
        
        String va = vaHelper.createNewVA(vaAmount, bank);
        try {
            Thread.sleep(1000);
        } catch(InterruptedException e) {
            return null;
        }
    
        return va;
    }

    @Override
    public PaymentResponse pay(String va, int payAmount, String day, String time) throws ExecutionException, InterruptedException {

        CompletableFuture<Boolean> isHoliday = CompletableFuture.supplyAsync(() -> holidayApi.isHoliday(day));
        CompletableFuture<BankApi> bankApi = CompletableFuture.supplyAsync(() ->{
            try{
                return vaHelper.getBankByVA(va);
            }catch (NoSuchElementException e) {
                throw new CompletionException(e);
            }
        }).exceptionally(err -> {
            if (err instanceof CompletionException && err.getCause() instanceof NoSuchElementException) {
                return null;
            }
            return null;
        });

        CompletableFuture<Integer> vaAmount = CompletableFuture.supplyAsync(() ->{
            try{
                return vaHelper.getVAAmount(va);
            }catch (NoSuchElementException e) {
                throw new CompletionException(e);
            }
        }).exceptionally(err -> {
            if (err instanceof CompletionException && err.getCause() instanceof NoSuchElementException) {
                return null;
            }
            return null;
        });

        BiFunction<Integer, BankApi, Boolean> closed =
                (jumlah, bank) -> bank.isBankClosed(time, jumlah);
        CompletableFuture<Boolean> finalClosed = vaAmount.thenCombineAsync(bankApi, closed);

        CompletableFuture<String> errorMsg = vaAmount.thenApplyAsync(val -> vaHelper.validatePayment(va, val, payAmount));
        CompletableFuture<Boolean> success = bankApi.thenApplyAsync(val -> val.pay(payAmount));




        if(isHoliday.get()) return new PaymentResponse(0, "Cannot pay on holidays");


        if (bankApi.get() == null || vaAmount.get() == null) {
            return new PaymentResponse(0, "VA number not found");
        }

        if(finalClosed.get()) return new PaymentResponse(0, "Bank already closed, please try again tomorrow");

        if(!errorMsg.get().equals("")) return new PaymentResponse(0, errorMsg.get());

        success.thenAcceptAsync(ss -> vaHelper.logVAPayment(va, ss));
        boolean paymentSuccessful = success.get();
        if(!paymentSuccessful) return new PaymentResponse(0, "Payment unsuccessfull, please try again");
        else return new PaymentResponse(1, "Payment successfull");



    }




}
