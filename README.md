# Лаборатори №8: Mock объектууд — Mockito-гоор хамаарлыг орлуулж тестлэх

**Оюутан:** Х.Түмэн-Амгалан
**Код:** B232270063
**Хичээл:** F.CSA313 — Программ хангамжийн чанарын баталгаа ба тест (2026)

## Орчин

```
$ java -version
openjdk version "17.0.20.1" 2026-08-18
OpenJDK Runtime Environment Temurin-17.0.20.1+1 (build 17.0.20.1+1)
OpenJDK 64-Bit Server VM Temurin-17.0.20.1+1 (build 17.0.20.1+1, mixed mode, sharing)

$ mvn -version
Apache Maven 3.9.9 (8e8579a9e76f7d015ee5ec7bfcdc97d260186937)
Maven home: C:\Users\tumee\tools\apache-maven-3.9.9
Java version: 17.0.20.1, vendor: Eclipse Adoptium, runtime: C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot
Default locale: en_US, platform encoding: UTF-8
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"
```

> Эх сурвалж: [`results/versions.txt`](results/versions.txt). JDK 17 тул «Java agent has been
> loaded dynamically» анхааруулга гараагүй (тэр нь JDK 21+-д гардаг).

## Төслийн бүтэц

```
lab8-mockito/
├── pom.xml                         багшийн starter (өөрчлөөгүй) — JUnit 5.10.2 + Mockito 5.11.0
├── .mvn/jvm.config                 Maven JVM-ийг UTF-8 болгох (Windows-ийн Cp1252-оос сэргийлэх)
├── src/main/java/mn/edu/must/sqat/ багшийн starter (өөрчлөөгүй, мутацийн дараа сэргээсэн)
│   ├── Student.java, StudentRepository.java, EmailSender.java
│   └── CourseRegistrationService.java     ← тестлэгдэх класс
├── src/test/java/mn/edu/must/sqat/
│   └── CourseRegistrationServiceTest.java ← A1–A8
└── results/
    ├── mvn-test.txt                    ногоон гаралт (10 тест)
    ├── mvn-test-mutant-1.txt … -3.txt  Хэсэг B
    ├── mvn-test-mutant-3-survived.txt  A6-г засахаас ӨМНӨ мутант 3 амьд үлдсэн гаралт
    ├── mvn-test-a8-strict.txt          A8-ийг lenient()-гүй ажиллуулсан гаралт
    └── versions.txt
```

---

## Хэсэг A: Найман тест

```bash
mkdir -p results && mvn test 2>&1 | tee results/mvn-test.txt
```

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Тестийн тоо: 10** — 7 энгийн тест (`@Test`) + A8-ийн 3 мөр (`@CsvSource`-ийн мөр бүр тусдаа тест).

> Эх сурвалж: [`results/mvn-test.txt`](results/mvn-test.txt)

| № | Тестийн метод | Гол хэрэгсэл | Юуг шалгана |
|---|---|---|---|
| A1 | `register_activeStudentWithPrerequisites_returnsOkSavesAndSendsEmail` | `when` + `verify` | "OK", `saveRegistration` дуудагдсан, и-мэйл оюутны хаяг руу хичээлийн кодтой явсан |
| A2 | `register_unknownStudent_returnsErrorNoStudentAndDoesNotSaveOrEmail` | `verify(..., never())` | "ERROR_NO_STUDENT", хадгалаагүй, и-мэйл яваагүй |
| A3 | `register_inactiveStudent_returnsErrorInactiveAndNeverTouchesEmail` | `verifyNoInteractions` | "ERROR_INACTIVE_STUDENT", `emailSender`-тэй огт харьцаагүй |
| A4 | `register_missingPrerequisite_returnsErrorPrerequisitesAndDoesNotSave` | `when` + `never` | CS201 дутуу → "ERROR_PREREQUISITES", хадгалаагүй |
| A5 | `register_emailSenderThrows_stillReturnsOkAndRegistrationIsSaved` | `doThrow(...).when(...)` | SMTP унасан ч "OK", бүртгэл хадгалагдсан |
| A6 | `register_nullStudentIdOrCourseId_throwsIllegalArgumentAndTouchesNoDependency` | `assertThrows` | `null` studentId ба `null` courseId → `IllegalArgumentException`; хамаарлуудтай огт харьцаагүй |
| A7 | `register_success_emailBodyContainsCourseCode` | `ArgumentCaptor` | Илгээсэн и-мэйлийн биеийг барьж, дотор нь "CS313" байгааг шалгана |
| A8 | `register_errorClass_returnsMatchingErrorAndDoesNotSaveOrEmail` | `@ParameterizedTest` + `@CsvSource` | NO_STUDENT / INACTIVE / NO_COURSE гурван ангийг нэг тестээр |

**Нэрлэлт:** `юуг_ямарНөхцөлд_юуХүлээгдэнэ` (метод_нөхцөл_үр дүн).
**Matcher-ийн дүрэм:** нэг дуудалтад matcher хэрэглэсэн бол бүх аргументад хэрэглэсэн —
жишээ нь `verify(emailSender).send(eq(EMAIL), anyString(), contains(COURSE))`,
`verify(emailSender).send(eq(EMAIL), eq("Бүртгэл баталгаажлаа"), body.capture())`.

### A8-д яагаад `lenient()` хэрэгтэй болсон бэ

A8-ийн мөр бүр `register()`-ийн өөр салбараар гарч, бусад stub-ууд ашиглагдахгүй үлддэг
(жишээ нь NO_STUDENT мөрөнд `courseExists`, `prerequisites` хэзээ ч дуудагдахгүй) тул
MockitoExtension-ий strict stubs горим үүнийг "хэрэггүй stub" гэж үзэж
`UnnecessaryStubbingException` шидсэн — `lenient()`-гүй ажиллуулахад **3 мөр бүгд Errors: 3**
болсон ([`results/mvn-test-a8-strict.txt`](results/mvn-test-a8-strict.txt)). Иймд зөвхөн A8-ийн
stub-уудыг `lenient().when(...)` болгосон; бусад тестэд strict горим хэвээр тул тэнд хэрэггүй
stub бичвэл Mockito анхааруулсаар байна.

---

## Хэсэг B: Тестээ тестлэх — мини мутаци

Мутант бүрийн дараа `CourseRegistrationService.java`-г нөөц хувилбараас нь сэргээсэн;
эцэст нь starter-ийн эх файлтай байт бүрээр ижил гэдгийг `cmp`-ээр шалгасан.

| Мутант | Эвдрэл | Унасан тест | Мессеж | Үр дүн | Файл |
|---|---|---|---|---|---|
| **1** | `if (!student.active()) ...` комментолсон | **A3** `register_inactiveStudent_...` | `expected: <ERROR_INACTIVE_STUDENT> but was: <ERROR_NO_COURSE>` | Failures: **2**, BUILD FAILURE | [`mvn-test-mutant-1.txt`](results/mvn-test-mutant-1.txt) |
|  |  | **A8 [2]** INACTIVE мөр | `expected: <ERROR_INACTIVE_STUDENT> but was: <OK>` |  |  |
| **2** | `repository.saveRegistration(...)` комментолсон | **A1** `register_activeStudent...` | `Wanted but not invoked: repository.saveRegistration(...)` | Failures: **2**, BUILD FAILURE | [`mvn-test-mutant-2.txt`](results/mvn-test-mutant-2.txt) |
|  |  | **A5** `register_emailSenderThrows_...` | `Wanted but not invoked: repository.saveRegistration(...)` |  |  |
| **3** | эхний `if`-ээс `\|\| courseId == null` устгасан | *(засахаас өмнө)* **нэг ч тест унаагүй** | — | Failures: 0, BUILD SUCCESS ⚠️ | [`mvn-test-mutant-3-survived.txt`](results/mvn-test-mutant-3-survived.txt) |
|  |  | *(A6-г засасны дараа)* **A6** `register_nullStudentIdOrCourseId_...` | `Expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown.` | Failures: **1**, BUILD FAILURE | [`mvn-test-mutant-3.txt`](results/mvn-test-mutant-3.txt) |

Гурван мутацид бүгд **Failures** гарсан, `UnnecessaryStubbingException` (Errors) гараагүй.

### Мутант 3 — тестийн цоорхой ба засвар

A6 анх зөвхөн `register(null, "CS313")`-ийг шалгадаг байсан тул `courseId`-ийн null шалгалтыг
устгахад ч тест ногоон үлдсэн — мутант **амьд үлдсэн**. A6-д
`assertThrows(IllegalArgumentException.class, () -> service.register(STUDENT_ID, null))`-ийг
нэмж хоёр аргумент тус бүрийн null-ийг шалгадаг болгоход мутант баригдсан (A6 унав). Тестийн
тоог 10 хэвээр байлгахын тулд шинэ метод биш, A6 дотроо хоёр дахь `assertThrows` нэмсэн.

### Мутацийн дүгнэлт

Мутант 1 дээр A8-ийн INACTIVE мөр `OK` буцаасан нь бодит үр дагаврыг тод харуулсан —
идэвхгүй оюутан **амжилттай бүртгэгдэх** байсан; харин A3 `ERROR_NO_COURSE` авсан нь A3-ийн
stub-д `courseExists` байхгүй (Mockito-гийн анхдагч `false`) учраас. Мутант 2-ыг зөвхөн
`verify(repository).saveRegistration(...)` бүхий A1, A5 барьсан: A7 зөвхөн и-мэйлийг шалгадаг
тул хадгалалт алга болсныг анзаараагүй — үр дүн ("OK") зөв ч гаж нөлөө (хадгалалт) алга
болсныг зөвхөн `verify` л илрүүлдэг. Мутант 3 нь "бүх тест ногоон" гэдэг нь "бүх зан төлөв
шалгагдсан" гэсэн үг биш болохыг баталсан.

---

## Дүгнэлт

Mock нь Mockito-гийн ажиллах үед үүсгэсэн, ямар ч логикгүй орлуулагч бөгөөд хариуг нь тест
бүр `when(...).thenReturn(...)`-ээр өөрөө тохируулж, дараа нь `verify`-ээр **хэрхэн
дуудагдсаныг** шалгадаг бол гараар бичсэн fake нь жишээ нь `HashMap` дээр суурилсан жинхэнэ
(гэхдээ хялбаршуулсан) `StudentRepository` хэрэгжүүлэлт бөгөөд **төлөвийг** (хадгалагдсан
бүртгэл) шалгахад тохиромжтой. Mock нь `doThrow`-оор SMTP унах мэт бодит орчинд гаргахад хэцүү
нөхцөлийг нэг мөрөөр дуурайлгадгаараа давуу (A5), харин fake нь олон тестэд дахин
ашиглагдаж, интерфейсийн гэрээг илүү бодитоор дагадаг. Mock хийх ёсгүй зүйлс: тестлэгдэж буй
класс өөрөө (`CourseRegistrationService`), `Student` мэт энгийн өгөгдлийн объект (record) ба
`List` зэрэг JDK-ийн утгын төрлүүд — эдгээрийг жинхэнээр нь үүсгэх нь илүү хялбар, найдвартай;
мөн өөрийн эзэмшдэггүй гадаад API-г шууд mock хийхээс илүүтэй түүнийг өөрийн интерфейсээр
(`EmailSender`) ороож mock хийх нь зөв. `verify`-г хэтрүүлбэл тест хэрэгжүүлэлтийн
дэлгэрэнгүйд (дуудалтын дараалал, тоо, яг ямар аргументаар) хэт уягдаж, зан төлөв өөрчлөгдөөгүй
ч код refactor хийхэд л унадаг "хэврэг" тест болно. Иймд энэ лабд `verify`-г зөвхөн
спецификацид бичигдсэн гаж нөлөөнд (хадгалалт хийгдсэн эсэх, и-мэйл явсан эсэх) хэрэглэсэн —
мутант 2-ыг яг эдгээр `verify` барьсан нь тэдгээр нь илүүдэл биш, зайлшгүй байсныг харуулав.
Нийтдээ mock нь хамаарлыг тусгаарлаж хурдан, тодорхой нэгжийн тест бичих боломж олгодог
ч, мутант 3 шиг цоорхойг илрүүлэхийн тулд тестийг өөрийг нь "тестлэх" хэрэгтэй.
