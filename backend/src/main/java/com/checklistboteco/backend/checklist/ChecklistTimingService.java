package com.checklistboteco.backend.checklist;

import com.checklistboteco.backend.model.Models.*;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@ApplicationScoped
public class ChecklistTimingService {
    private static final long WARNING_MILLIS=30*60*1000L;

    public ChecklistOverview overview(LocalDate date,long now,List<Activity> activities,List<Completion> completions,List<PublicUser> users,ChecklistSchedule schedule){
        var result=new ChecklistOverview(); result.serviceDate=date.toString();
        OperatingDaySchedule day=schedule.days.get(date.getDayOfWeek());
        if(day==null||!day.active) return result;
        Map<String,PublicUser> userById=new HashMap<>(); users.forEach(u->userById.put(u.id,u));
        for(Activity activity:activities){
            normalize(activity);
            if(!isDue(activity,date)) continue;
            var occurrence=new ChecklistOccurrence();
            occurrence.occurrenceId=activity.id+":"+date; occurrence.serviceDate=date.toString(); occurrence.activityId=activity.id;
            occurrence.activityName=activity.name; occurrence.area=activity.area; occurrence.executionPhase=activity.executionPhase;
            occurrence.estimatedDurationMinutes=activity.estimatedDurationMinutes; occurrence.assigneeIds=new ArrayList<>(activity.assigneeIds);
            occurrence.assigneeNames=activity.assigneeIds.stream().map(userById::get).filter(Objects::nonNull).map(u->u.name).toList();
            occurrence.deadlineAt=deadline(date,day,activity.executionPhase,schedule.timezone);
            occurrence.recommendedStartAt=occurrence.deadlineAt-activity.estimatedDurationMinutes*60_000L;
            occurrence.completion=completions.stream().filter(c->Objects.equals(c.activityId,activity.id)&&Objects.equals(serviceDate(c,schedule.timezone),date.toString())).max(Comparator.comparingLong(c->c.completedAt)).orElse(null);
            if(occurrence.completion!=null){
                occurrence.status=ChecklistTimingStatus.COMPLETED; occurrence.completedByName=Optional.ofNullable(userById.get(occurrence.completion.userId)).map(u->u.name).orElse(null);
                occurrence.completion.isLate=occurrence.completion.completedAt>occurrence.deadlineAt;
            }else if(now>occurrence.deadlineAt) occurrence.status=ChecklistTimingStatus.RED;
            else if(occurrence.deadlineAt-now<=WARNING_MILLIS) occurrence.status=ChecklistTimingStatus.YELLOW;
            else occurrence.status=ChecklistTimingStatus.GREEN;
            result.occurrences.add(occurrence);
            switch(occurrence.status){
                case GREEN -> { result.green++; result.totalRemainingMinutes+=activity.estimatedDurationMinutes; }
                case YELLOW -> { result.yellow++; result.totalRemainingMinutes+=activity.estimatedDurationMinutes; }
                case RED -> { result.red++; result.totalRemainingMinutes+=activity.estimatedDurationMinutes; }
                case COMPLETED -> result.completed++;
            }
        }
        result.occurrences.sort(Comparator.comparingInt((ChecklistOccurrence o)->rank(o.status)).thenComparingLong(o->o.deadlineAt));
        return result;
    }

    public long deadline(LocalDate date,OperatingDaySchedule day,ExecutionPhase phase,String timezone){
        String value=switch(phase){ case BEFORE_LUNCH->day.lunchTime; case BEFORE_OPENING->day.openingTime; case DURING_OPERATION->day.closingTime; };
        LocalTime time=LocalTime.parse(value); LocalDate target=date;
        if(phase==ExecutionPhase.DURING_OPERATION&&!time.isAfter(LocalTime.parse(day.entryTime))) target=date.plusDays(1);
        return ZonedDateTime.of(target,time,ZoneId.of(timezone)).toInstant().toEpochMilli();
    }

    public boolean isDue(Activity activity,LocalDate date){
        normalize(activity); if(!activity.activeWeekdays.contains(date.getDayOfWeek())) return false;
        if(activity.frequency==Frequency.DIARIO) return true;
        LocalDate anchor=parseAnchor(activity.recurrenceAnchorDate,date);
        if(date.isBefore(anchor)) return false;
        if(activity.frequency==Frequency.QUINZENAL) return ChronoUnit.DAYS.between(anchor,date)%14==0;
        return date.getDayOfMonth()==Math.min(anchor.getDayOfMonth(),date.lengthOfMonth());
    }

    public void normalize(Activity activity){
        if(activity.assigneeIds==null) activity.assigneeIds=new ArrayList<>();
        if(activity.activeWeekdays==null||activity.activeWeekdays.isEmpty()) activity.activeWeekdays=new ArrayList<>(List.of(DayOfWeek.TUESDAY,DayOfWeek.FRIDAY,DayOfWeek.SATURDAY,DayOfWeek.SUNDAY));
        if(activity.estimatedDurationMinutes<1) activity.estimatedDurationMinutes=15;
        if(activity.executionPhase==null) activity.executionPhase=ExecutionPhase.BEFORE_LUNCH;
    }

    private String serviceDate(Completion completion,String timezone){
        if(completion.serviceDate!=null&&!completion.serviceDate.isBlank()) return completion.serviceDate;
        return Instant.ofEpochMilli(completion.completedAt).atZone(ZoneId.of(timezone)).toLocalDate().toString();
    }
    private LocalDate parseAnchor(String value,LocalDate fallback){ try{return value==null||value.isBlank()?fallback:LocalDate.parse(value);}catch(Exception ignored){return fallback;} }
    private int rank(ChecklistTimingStatus status){ return switch(status){case RED->0;case YELLOW->1;case GREEN->2;case COMPLETED->3;}; }
}
