package com.checklistboteco.backend;

import com.checklistboteco.backend.checklist.ChecklistTimingService;
import com.checklistboteco.backend.model.Models.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChecklistTimingServiceTest {
    private final ChecklistTimingService service=new ChecklistTimingService();
    private final ChecklistSchedule schedule=ChecklistSchedule.defaults();
    private final ZoneId zone=ZoneId.of("America/Fortaleza");

    @Test void computesTuesdayWarningAndRecommendedStart(){
        Activity activity=activity(ExecutionPhase.BEFORE_LUNCH,45);
        LocalDate date=LocalDate.of(2026,7,7);
        long now=ZonedDateTime.of(date,LocalTime.of(16,40),zone).toInstant().toEpochMilli();
        ChecklistOccurrence result=service.overview(date,now,List.of(activity),List.of(),List.of(),schedule).occurrences.get(0);
        assertEquals(ChecklistTimingStatus.YELLOW,result.status);
        assertEquals(LocalTime.of(16,15),Instant.ofEpochMilli(result.recommendedStartAt).atZone(zone).toLocalTime());
    }

    @Test void marksCompletionLateUsingServiceDate(){
        Activity activity=activity(ExecutionPhase.BEFORE_OPENING,15);
        LocalDate date=LocalDate.of(2026,7,11);
        Completion completion=new Completion(); completion.activityId=activity.id; completion.userId="u1"; completion.serviceDate=date.toString();
        completion.completedAt=ZonedDateTime.of(date,LocalTime.of(12,5),zone).toInstant().toEpochMilli();
        PublicUser user=new PublicUser(); user.id="u1"; user.name="Funcionário";
        ChecklistOccurrence result=service.overview(date,completion.completedAt,List.of(activity),List.of(completion),List.of(user),schedule).occurrences.get(0);
        assertEquals(ChecklistTimingStatus.COMPLETED,result.status);
        assertTrue(result.completion.isLate);
        assertEquals("Funcionário",result.completedByName);
    }

    @Test void doesNotGenerateOccurrencesOnClosedDays(){
        ChecklistOverview result=service.overview(LocalDate.of(2026,7,8),System.currentTimeMillis(),List.of(activity(ExecutionPhase.BEFORE_LUNCH,15)),List.of(),List.of(),schedule);
        assertTrue(result.occurrences.isEmpty());
    }

    private Activity activity(ExecutionPhase phase,int duration){
        Activity value=new Activity(); value.id="a1"; value.name="Preparar salão"; value.area=Area.ATENDIMENTO; value.frequency=Frequency.DIARIO;
        value.executionPhase=phase; value.estimatedDurationMinutes=duration; value.activeWeekdays=new ArrayList<>(List.of(DayOfWeek.TUESDAY,DayOfWeek.SATURDAY)); return value;
    }
}
