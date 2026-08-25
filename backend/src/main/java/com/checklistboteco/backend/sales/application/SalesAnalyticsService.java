package com.checklistboteco.backend.sales.application;

import static com.checklistboteco.backend.sales.domain.SalesModels.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.ToLongFunction;

@ApplicationScoped
public class SalesAnalyticsService {
    @Inject SalesQueryService salesQueries;

    public MonthCompareResponse compareMonth(String datasetId,MonthCompareRequest request){
        if(request==null||request.focusMonth==null||request.focusMonth.isBlank()) throw new IllegalArgumentException("focusMonth obrigatório (YYYY-MM)");
        YearMonth focusMonth=parseMonth(request.focusMonth);
        SaleQuery unbounded=copyFilters(request,null,null);
        List<Sale> available=salesQueries.filteredSales(datasetId,unbounded);
        if(available.isEmpty()) throw new IllegalArgumentException("Não há vendas disponíveis para os filtros informados");
        ImportSchema schema=salesQueries.schema(datasetId);
        LocalDate coverageFrom=Objects.requireNonNull(schema.coverageFrom,"Cobertura inicial de vendas ausente");
        LocalDate coverageTo=Objects.requireNonNull(schema.coverageTo,"Cobertura final de vendas ausente");
        LocalDate from=request.from==null?coverageFrom:request.from;
        LocalDate to=request.to==null?coverageTo:request.to;
        if(to.isBefore(from)) throw new IllegalArgumentException("Período final anterior ao inicial");

        List<Sale> scoped=salesQueries.filteredSales(datasetId,copyFilters(request,from,to));
        Map<YearMonth,List<Sale>> byMonth=new TreeMap<>();
        scoped.stream().filter(sale->sale.saleDate!=null).forEach(sale->byMonth.computeIfAbsent(YearMonth.from(sale.saleDate),key->new ArrayList<>()).add(sale));
        List<Sale> focusSales=byMonth.getOrDefault(focusMonth,List.of());
        if(focusSales.isEmpty()) throw new IllegalArgumentException("Não há vendas no mês "+focusMonth);

        List<YearMonth> baselineMonths=completeBaselineMonths(focusMonth,from,to,coverageFrom,coverageTo,byMonth);
        if(baselineMonths.isEmpty()) throw new IllegalArgumentException("Não há outros meses completos para calcular a média de comparação");
        List<PeriodSnapshot> baselineSnapshots=baselineMonths.stream().map(month->snapshot(month.toString(),byMonth.get(month))).toList();
        PeriodSnapshot focus=snapshot(focusMonth.toString(),focusSales);
        PeriodSnapshot baseline=averageSnapshot("Média de "+baselineMonths.size()+" outros meses completos",baselineSnapshots);

        MonthCompareResponse result=new MonthCompareResponse();
        result.datasetId=dataset(datasetId);
        result.focusMonth=focusMonth.toString();
        result.baselineLabel=baseline.label;
        result.sourceCoverageFrom=coverageFrom;
        result.sourceCoverageTo=coverageTo;
        result.baselineMonths=baselineMonths.stream().map(YearMonth::toString).toList();
        result.focus=focus;
        result.baselineAverage=baseline;
        result.delta=delta(focus,baseline);
        result.weekdays=weekdayComparisons(focusSales,baselineMonths,byMonth,focus,baseline);
        result.topProductDrivers=productDrivers(focusSales,baselineMonths,byMonth,focus,baseline,Math.max(3,Math.min(20,request.topProducts)));
        result.topDays=topDays(focusSales,10);
        result.filtersApplied=filters(request,from,to);
        result.findings=findings(result);
        return result;
    }

    private static List<YearMonth> completeBaselineMonths(YearMonth focus,LocalDate from,LocalDate to,LocalDate coverageFrom,LocalDate coverageTo,Map<YearMonth,List<Sale>> byMonth){
        List<YearMonth> months=new ArrayList<>();
        for(YearMonth month=YearMonth.from(from);!month.isAfter(YearMonth.from(to));month=month.plusMonths(1)){
            if(month.equals(focus)||!byMonth.containsKey(month)) continue;
            LocalDate start=month.atDay(1),end=month.atEndOfMonth();
            if(start.isBefore(from)||end.isAfter(to)||start.isBefore(coverageFrom)||end.isAfter(coverageTo)) continue;
            months.add(month);
        }
        return months;
    }

    private static PeriodSnapshot snapshot(String label,List<Sale> sales){
        PeriodSnapshot result=new PeriodSnapshot();
        result.label=label;
        result.lineCount=sales.size();
        result.totalInCents=sales.stream().mapToLong(sale->sale.totalInCents).sum();
        result.serviceChargeInCents=sales.stream().mapToLong(sale->sale.serviceChargeInCents).sum();
        result.totalQuantity=sales.stream().map(sale->sale.quantity==null?BigDecimal.ZERO:sale.quantity).reduce(BigDecimal.ZERO,BigDecimal::add);
        result.daysWithSales=(int)sales.stream().map(sale->sale.saleDate).filter(Objects::nonNull).distinct().count();
        result.averageDailyRevenueInCents=divide(result.totalInCents,result.daysWithSales);
        result.averageLineRevenueInCents=divide(result.totalInCents,result.lineCount);
        long weekend=sales.stream().filter(sale->isWeekend(sale.saleDate)).mapToLong(sale->sale.totalInCents).sum();
        result.weekendRevenueSharePercent=percent(weekend,result.totalInCents);
        return result;
    }

    private static PeriodSnapshot averageSnapshot(String label,List<PeriodSnapshot> values){
        PeriodSnapshot result=new PeriodSnapshot();
        result.label=label;
        int count=values.size();
        result.totalInCents=averageLong(values,value->value.totalInCents);
        result.serviceChargeInCents=averageLong(values,value->value.serviceChargeInCents);
        result.lineCount=averageLong(values,value->value.lineCount);
        result.totalQuantity=values.stream().map(value->value.totalQuantity).reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(count),2,RoundingMode.HALF_UP);
        result.daysWithSales=(int)averageLong(values,value->value.daysWithSales);
        result.averageDailyRevenueInCents=averageLong(values,value->value.averageDailyRevenueInCents);
        result.averageLineRevenueInCents=averageLong(values,value->value.averageLineRevenueInCents);
        result.weekendRevenueSharePercent=values.stream().mapToDouble(value->value.weekendRevenueSharePercent).average().orElse(0);
        return result;
    }

    private static MonthDelta delta(PeriodSnapshot focus,PeriodSnapshot baseline){
        MonthDelta result=new MonthDelta();
        result.revenueInCents=focus.totalInCents-baseline.totalInCents;
        result.quantity=focus.totalQuantity.subtract(baseline.totalQuantity);
        result.lineCount=focus.lineCount-baseline.lineCount;
        result.averageDailyRevenueInCents=focus.averageDailyRevenueInCents-baseline.averageDailyRevenueInCents;
        result.averageLineRevenueInCents=focus.averageLineRevenueInCents-baseline.averageLineRevenueInCents;
        result.revenuePercent=changePercent(focus.totalInCents,baseline.totalInCents);
        result.quantityPercent=changePercent(focus.totalQuantity,baseline.totalQuantity);
        result.lineCountPercent=changePercent(focus.lineCount,baseline.lineCount);
        result.averageDailyRevenuePercent=changePercent(focus.averageDailyRevenueInCents,baseline.averageDailyRevenueInCents);
        result.averageLineRevenuePercent=changePercent(focus.averageLineRevenueInCents,baseline.averageLineRevenueInCents);
        result.weekendSharePoints=round(focus.weekendRevenueSharePercent-baseline.weekendRevenueSharePercent);
        return result;
    }

    private static List<WeekdayComparison> weekdayComparisons(List<Sale> focusSales,List<YearMonth> baselineMonths,Map<YearMonth,List<Sale>> byMonth,PeriodSnapshot focus,PeriodSnapshot baseline){
        List<WeekdayComparison> result=new ArrayList<>();
        for(DayOfWeek day:DayOfWeek.values()){
            long focusRevenue=revenueForDay(focusSales,day);
            long baselineRevenue=Math.round(baselineMonths.stream().mapToLong(month->revenueForDay(byMonth.get(month),day)).average().orElse(0));
            WeekdayComparison row=new WeekdayComparison();
            row.dayOfWeek=day.name();
            row.label=day.getDisplayName(TextStyle.FULL,new Locale("pt","BR"));
            row.focusRevenueInCents=focusRevenue;
            row.baselineAverageRevenueInCents=baselineRevenue;
            row.focusSharePercent=percent(focusRevenue,focus.totalInCents);
            row.baselineSharePercent=percent(baselineRevenue,baseline.totalInCents);
            row.shareDeltaPoints=round(row.focusSharePercent-row.baselineSharePercent);
            result.add(row);
        }
        result.sort(Comparator.comparingLong((WeekdayComparison row)->row.focusRevenueInCents-row.baselineAverageRevenueInCents).reversed());
        return result;
    }

    private static List<ProductDriver> productDrivers(List<Sale> focusSales,List<YearMonth> baselineMonths,Map<YearMonth,List<Sale>> byMonth,PeriodSnapshot focus,PeriodSnapshot baseline,int limit){
        Map<String,List<Sale>> focusByProduct=groupByProduct(focusSales);
        Set<String> products=new LinkedHashSet<>(focusByProduct.keySet());
        baselineMonths.forEach(month->products.addAll(groupByProduct(byMonth.get(month)).keySet()));
        List<ProductDriver> result=new ArrayList<>();
        for(String product:products){
            List<Sale> focusValues=focusByProduct.getOrDefault(product,List.of());
            long focusRevenue=focusValues.stream().mapToLong(sale->sale.totalInCents).sum();
            BigDecimal focusQuantity=quantity(focusValues);
            long baselineRevenue=Math.round(baselineMonths.stream().mapToLong(month->groupByProduct(byMonth.get(month)).getOrDefault(product,List.of()).stream().mapToLong(sale->sale.totalInCents).sum()).average().orElse(0));
            BigDecimal baselineQuantity=baselineMonths.stream().map(month->quantity(groupByProduct(byMonth.get(month)).getOrDefault(product,List.of()))).reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(baselineMonths.size()),2,RoundingMode.HALF_UP);
            ProductDriver row=new ProductDriver();
            row.product=product;
            row.focusRevenueInCents=focusRevenue;
            row.baselineAverageRevenueInCents=baselineRevenue;
            row.revenueDeltaInCents=focusRevenue-baselineRevenue;
            row.focusQuantity=focusQuantity;
            row.baselineAverageQuantity=baselineQuantity;
            row.focusRevenueSharePercent=percent(focusRevenue,focus.totalInCents);
            row.baselineRevenueSharePercent=percent(baselineRevenue,baseline.totalInCents);
            row.shareDeltaPoints=round(row.focusRevenueSharePercent-row.baselineRevenueSharePercent);
            result.add(row);
        }
        boolean focusAboveBaseline=focus.totalInCents>=baseline.totalInCents;
        Comparator<ProductDriver> order=Comparator.comparingLong(row->row.revenueDeltaInCents);
        if(focusAboveBaseline) order=order.reversed();
        return result.stream()
            .filter(row->focusAboveBaseline?row.revenueDeltaInCents>0:row.revenueDeltaInCents<0)
            .sorted(order)
            .limit(limit)
            .toList();
    }

    private static List<TopSalesDay> topDays(List<Sale> sales,int limit){
        Map<LocalDate,List<Sale>> byDate=new HashMap<>();
        sales.forEach(sale->byDate.computeIfAbsent(sale.saleDate,key->new ArrayList<>()).add(sale));
        return byDate.entrySet().stream().map(entry->{
            TopSalesDay row=new TopSalesDay();
            row.date=entry.getKey();
            row.dayOfWeek=entry.getKey().getDayOfWeek().name();
            row.label=entry.getKey().getDayOfWeek().getDisplayName(TextStyle.FULL,new Locale("pt","BR"));
            row.totalInCents=entry.getValue().stream().mapToLong(sale->sale.totalInCents).sum();
            row.quantity=quantity(entry.getValue());
            row.lineCount=entry.getValue().size();
            return row;
        }).sorted(Comparator.comparingLong((TopSalesDay row)->row.totalInCents).reversed()).limit(limit).toList();
    }

    private static List<String> findings(MonthCompareResponse result){
        List<String> values=new ArrayList<>();
        values.add("Faturamento de "+result.focusMonth+" ficou "+relativeChange(result.delta.revenuePercent)+" da média dos outros meses completos ("+money(result.delta.revenueInCents)+" de diferença).");
        values.add("Quantidade vendida ficou "+relativeChange(result.delta.quantityPercent)+" e o número de linhas de venda "+relativeChange(result.delta.lineCountPercent)+".");
        values.add("Receita média por dia com venda ficou "+relativeChange(result.delta.averageDailyRevenuePercent)+"; valor médio por linha ficou "+relativeChange(result.delta.averageLineRevenuePercent)+".");
        values.add("Participação do fim de semana variou "+signedPoints(result.delta.weekendSharePoints)+" ponto(s) percentual(is).");
        if(!result.topProductDrivers.isEmpty()){
            ProductDriver top=result.topProductDrivers.get(0);
            values.add(top.product+" teve a maior contribuição observada: "+money(Math.abs(top.revenueDeltaInCents))+" "+(top.revenueDeltaInCents>=0?"acima":"abaixo")+" da média mensal.");
        }
        if(!result.topDays.isEmpty()){
            TopSalesDay top=result.topDays.get(0);
            values.add("Dia de maior faturamento no mês: "+top.date+" ("+top.label+"), com "+money(top.totalInCents)+".");
        }
        return values;
    }

    private static SaleQuery copyFilters(MonthCompareRequest request,LocalDate from,LocalDate to){
        SaleQuery copy=new SaleQuery();
        copy.from=from;
        copy.to=to;
        copy.categories=request.categories==null?List.of():new ArrayList<>(request.categories);
        copy.locations=request.locations==null?List.of():new ArrayList<>(request.locations);
        copy.sellers=request.sellers==null?List.of():new ArrayList<>(request.sellers);
        copy.minTotalInCents=request.minTotalInCents;
        copy.maxTotalInCents=request.maxTotalInCents;
        copy.text=request.text;
        copy.attributes=request.attributes==null?Map.of():new LinkedHashMap<>(request.attributes);
        return copy;
    }

    private static List<String> filters(MonthCompareRequest request,LocalDate from,LocalDate to){
        List<String> values=new ArrayList<>(List.of("focusMonth="+request.focusMonth,"from="+from,"to="+to));
        if(request.locations!=null&&!request.locations.isEmpty()) values.add("locations="+request.locations);
        if(request.categories!=null&&!request.categories.isEmpty()) values.add("categories="+request.categories);
        if(request.sellers!=null&&!request.sellers.isEmpty()) values.add("sellers="+request.sellers);
        if(request.text!=null&&!request.text.isBlank()) values.add("text="+request.text);
        return values;
    }

    private static Map<String,List<Sale>> groupByProduct(List<Sale> sales){
        Map<String,List<Sale>> values=new LinkedHashMap<>();
        sales.forEach(sale->values.computeIfAbsent(sale.description==null||sale.description.isBlank()?"Sem produto":sale.description,key->new ArrayList<>()).add(sale));
        return values;
    }
    private static BigDecimal quantity(List<Sale> values){ return values.stream().map(sale->sale.quantity==null?BigDecimal.ZERO:sale.quantity).reduce(BigDecimal.ZERO,BigDecimal::add); }
    private static long revenueForDay(List<Sale> values,DayOfWeek day){ return values.stream().filter(sale->sale.saleDate!=null&&sale.saleDate.getDayOfWeek()==day).mapToLong(sale->sale.totalInCents).sum(); }
    private static boolean isWeekend(LocalDate date){ return date!=null&&(date.getDayOfWeek()==DayOfWeek.SATURDAY||date.getDayOfWeek()==DayOfWeek.SUNDAY); }
    private static long averageLong(List<PeriodSnapshot> values,ToLongFunction<PeriodSnapshot> getter){ return Math.round(values.stream().mapToLong(getter).average().orElse(0)); }
    private static long divide(long value,long divisor){ return divisor<=0?0:Math.round(value/(double)divisor); }
    private static double percent(long part,long total){ return total==0?0:round(part*100.0/total); }
    private static double changePercent(long value,long baseline){ return baseline==0?0:round((value-baseline)*100.0/baseline); }
    private static double changePercent(BigDecimal value,BigDecimal baseline){ return baseline==null||baseline.compareTo(BigDecimal.ZERO)==0?0:round(value.subtract(baseline).multiply(BigDecimal.valueOf(100)).divide(baseline,4,RoundingMode.HALF_UP).doubleValue()); }
    private static double round(double value){ return Math.round(value*100.0)/100.0; }
    private static YearMonth parseMonth(String value){ try{return YearMonth.parse(value.trim());}catch(Exception e){throw new IllegalArgumentException("focusMonth inválido; use YYYY-MM");} }
    private static String dataset(String value){ return value==null||value.isBlank()?"sales":value.trim(); }
    private static String relativeChange(double value){ return NumberFormat.getNumberInstance(new Locale("pt","BR")).format(Math.abs(value))+"% "+(value>=0?"acima":"abaixo"); }
    private static String signedPoints(double value){ return (value>=0?"+":"")+NumberFormat.getNumberInstance(new Locale("pt","BR")).format(value); }
    private static String money(long cents){ return NumberFormat.getCurrencyInstance(new Locale("pt","BR")).format(BigDecimal.valueOf(cents,2)); }
}
