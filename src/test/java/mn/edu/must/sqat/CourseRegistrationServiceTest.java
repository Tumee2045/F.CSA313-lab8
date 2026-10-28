package mn.edu.must.sqat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseRegistrationServiceTest {

    private static final String STUDENT_ID = "B232270063";
    private static final String EMAIL = "b232270063@must.edu.mn";
    private static final String COURSE = "CS313";
    private static final String PREREQ = "CS201";

    @Mock StudentRepository repository;
    @Mock EmailSender emailSender;
    CourseRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new CourseRegistrationService(repository, emailSender);
    }

    private static Student activeStudent(List<String> coursesTaken) {
        return new Student(STUDENT_ID, EMAIL, true, coursesTaken);
    }

    /** Хэвийн замын stub: идэвхтэй оюутан, хичээл байгаа, урьдач нь CS201 (оюутан үзсэн). */
    private void stubHappyPath() {
        when(repository.findStudent(STUDENT_ID)).thenReturn(activeStudent(List.of(PREREQ)));
        when(repository.courseExists(COURSE)).thenReturn(true);
        when(repository.prerequisites(COURSE)).thenReturn(List.of(PREREQ));
    }

    // A1 — when + verify
    @Test
    void register_activeStudentWithPrerequisites_returnsOkSavesAndSendsEmail() {
        stubHappyPath();                                                   // Arrange

        String result = service.register(STUDENT_ID, COURSE);              // Act

        assertEquals("OK", result);                                        // Assert
        verify(repository).saveRegistration(STUDENT_ID, COURSE);
        verify(emailSender).send(eq(EMAIL), anyString(), contains(COURSE));
    }

    // A2 — verify(..., never())
    @Test
    void register_unknownStudent_returnsErrorNoStudentAndDoesNotSaveOrEmail() {
        when(repository.findStudent(STUDENT_ID)).thenReturn(null);

        String result = service.register(STUDENT_ID, COURSE);

        assertEquals("ERROR_NO_STUDENT", result);
        verify(repository, never()).saveRegistration(anyString(), anyString());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    // A3 — verifyNoInteractions
    @Test
    void register_inactiveStudent_returnsErrorInactiveAndNeverTouchesEmail() {
        when(repository.findStudent(STUDENT_ID))
                .thenReturn(new Student(STUDENT_ID, EMAIL, false, List.of(PREREQ)));

        String result = service.register(STUDENT_ID, COURSE);

        assertEquals("ERROR_INACTIVE_STUDENT", result);
        verifyNoInteractions(emailSender);
    }

    // A4 — when + never
    @Test
    void register_missingPrerequisite_returnsErrorPrerequisitesAndDoesNotSave() {
        when(repository.findStudent(STUDENT_ID)).thenReturn(activeStudent(List.of("CS101")));
        when(repository.courseExists(COURSE)).thenReturn(true);
        when(repository.prerequisites(COURSE)).thenReturn(List.of(PREREQ));

        String result = service.register(STUDENT_ID, COURSE);

        assertEquals("ERROR_PREREQUISITES", result);
        verify(repository, never()).saveRegistration(anyString(), anyString());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    // A5 — doThrow(...).when(...)
    @Test
    void register_emailSenderThrows_stillReturnsOkAndRegistrationIsSaved() {
        stubHappyPath();
        doThrow(new RuntimeException("SMTP сервер унасан"))
                .when(emailSender).send(anyString(), anyString(), anyString());

        String result = service.register(STUDENT_ID, COURSE);

        assertEquals("OK", result);
        verify(repository).saveRegistration(STUDENT_ID, COURSE);
    }

    // A6 — assertThrows
    @Test
    void register_nullStudentId_throwsIllegalArgumentAndTouchesNoDependency() {
        assertThrows(IllegalArgumentException.class, () -> service.register(null, COURSE));

        verifyNoInteractions(repository, emailSender);
    }

    // A7 — ArgumentCaptor
    @Test
    void register_success_emailBodyContainsCourseCode() {
        stubHappyPath();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

        service.register(STUDENT_ID, COURSE);

        verify(emailSender).send(eq(EMAIL), eq("Бүртгэл баталгаажлаа"), body.capture());
        assertTrue(body.getValue().contains(COURSE),
                "И-мэйлийн биед хичээлийн код байх ёстой, гэтэл: " + body.getValue());
    }

    // A8 — @ParameterizedTest + @CsvSource (3 алдааны анги)
    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource({
            "NO_STUDENT, ERROR_NO_STUDENT",
            "INACTIVE,   ERROR_INACTIVE_STUDENT",
            "NO_COURSE,  ERROR_NO_COURSE"
    })
    void register_errorClass_returnsMatchingErrorAndDoesNotSaveOrEmail(String scenario, String expected) {
        // Мөр бүр өөр замаар гарах тул бүх stub-ыг lenient() болгосон: жишээ нь NO_STUDENT
        // мөрөнд courseExists хэзээ ч дуудагдахгүй — strict stubs горимд энэ нь
        // UnnecessaryStubbingException болох байсан.
        Student student = switch (scenario) {
            case "NO_STUDENT" -> null;
            case "INACTIVE" -> new Student(STUDENT_ID, EMAIL, false, List.of(PREREQ));
            default -> activeStudent(List.of(PREREQ));
        };
        lenient().when(repository.findStudent(STUDENT_ID)).thenReturn(student);
        lenient().when(repository.courseExists(COURSE)).thenReturn(!"NO_COURSE".equals(scenario));
        lenient().when(repository.prerequisites(COURSE)).thenReturn(List.of(PREREQ));

        String result = service.register(STUDENT_ID, COURSE);

        assertEquals(expected, result);
        verify(repository, never()).saveRegistration(anyString(), anyString());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }
}
