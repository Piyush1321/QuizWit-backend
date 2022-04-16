package com.exam;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.simple.JSONObject;

import com.admin.Exam;
import com.admin.Section;
import com.admin.ViewExams;
import com.admin.ViewSections;
import com.config.Headers;
import com.config.Origin;
import com.questions.Question;
import com.student.Student;
import com.util.FisherYatesShuffle;

/**
 * Servlet implementation class StartExam
 */
@WebServlet("/StartExam")
public class StartExam extends HttpServlet {
	private static final long serialVersionUID = 1L;
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		HttpSession session = request.getSession();
		Headers.setRequiredHeaders(response, Origin.getExam());
		String examIdString = request.getParameter("examId");
		String email = request.getParameter("email");
		String password = request.getParameter("password");
		JSONObject json = new JSONObject();
		String success = "";
		String error = "";
		
		Student student = new Student();
		
		Boolean control = true;
		JSONObject errorLog = new JSONObject();
		Long currentTime = System.currentTimeMillis()/1000;
		

		if(session.getAttribute("ExamLoggedIn") != null && (boolean) session.getAttribute("ExamLoggedIn")) { 
			Integer studentId = (Integer) session.getAttribute("studentId");
			Integer examId = (Integer) session.getAttribute("examId"); // get exam Id from session 
			// get last attemptId from the database of the student
			try {
				Integer attemptId = Attempt.getAttemptId(examId, studentId); 
				if(attemptId == 0) { // attempt not given
					JSONObject exam = ViewExams.fetchExam(examId);
					json.put("examTitle", exam.get("title"));
					Long startTime = Long.parseLong((String) exam.get("startTime"))/1000;
					Integer windowTime = Integer.parseInt((String) exam.get("windowTime"));
					System.out.println("CurrentTime: " + currentTime);
					System.out.println("StartTime: " + startTime);
					System.out.println("WindowTime: " + (startTime + windowTime));
					if(currentTime >= startTime && currentTime <= (startTime + windowTime)) {

						Boolean examTimer = Exam.setEntireExamTimer(examId);
						Integer entireExamTimeDuration = 0;
						Integer endTime = -1;
						if(examTimer) {
							entireExamTimeDuration = Exam.duration(examId);
							endTime = (int) ((System.currentTimeMillis()/1000) + entireExamTimeDuration);
							json.put("entireExamTimeDuration", entireExamTimeDuration);
						}

						attemptId = Attempt.add(examId, studentId, endTime);
						if(attemptId > 0) {
							session.setAttribute("attemptId", attemptId);
							ArrayList<JSONObject> sections = ViewSections.fetchAllSections(examId);
							Boolean controlSectionNavigationEntry = true;
							Boolean sectionNavigation = Exam.sectionNavigation(examId);
							Integer access = 0;
							for(int i=0; i<sections.size(); i++) {
								JSONObject section = sections.get(i);
								Integer sectionId = Integer.parseInt((String) section.get("sectionId"));
								Integer setSectionTimer = Integer.parseInt((String) section.get("setSectionTimer"));
								Integer setQuestionTimer = Integer.parseInt((String) section.get("setQuestionTimer"));
								Integer sectionTimeDuration = Integer.parseInt((String) section.get("timeDuration"));
								Integer shuffleQuestions = Integer.parseInt((String) section.get("shuffleQuestions"));
								Boolean questionNavigation = (String) section.get("questionNavigation") == "1" ? true : false;
								Integer sectionEndTime = -1;
								
								if(i == 0) { // first section
									if(setSectionTimer == 1) {
										sectionEndTime = (int) ((System.currentTimeMillis()/1000) + sectionTimeDuration);
									}
								}
								
								access = sectionNavigation ? 1 : 0;
								
								if(i == 0)
									access = 1;
								
								Attempt.addSectionNavigationControl(attemptId, sectionId, access, sectionEndTime);
								
								ArrayList<Integer> questions = Section.getAllQuestionsId(sectionId);
								if(shuffleQuestions == 1) {
									questions = FisherYatesShuffle.shuffle(questions);
								}
								
								Integer questionNavigationAccess = 0;
								for(int j=0; j<questions.size(); j++) {
									Integer questionId = questions.get(j);
									Integer questionEndTime = -1;
									Integer questionTimeDuration = Question.duration(questionId);
									if(i == 0 && j == 0) {
										if(setQuestionTimer == 1) {
											questionEndTime = (int) ((System.currentTimeMillis()/1000) + questionTimeDuration);
										}
									}
									
									questionNavigationAccess = questionNavigation ? 1 : 0;
									
									if(i == 0 && j == 0)
										questionNavigationAccess = 1;
									
									Attempt.addQuestionNavigationControl(attemptId, questionId, questionNavigationAccess, questionEndTime);
								}
								
								for(Integer id : questions) {
									System.out.println(id);
								}
								
							}
							success = "Exam attempt settings done";
						
						}
						else {
							error = "Something went wrong while preparing your attempt";
						}
					}
					else {
						if(currentTime < startTime)
							error = "Exam is not started yet";
						else error = "Exam attempt time expired";
					}
				}
				else { // attempt already started
					Long entireExamEndTime = Attempt.duration(attemptId);
					Integer entireExamAvailableTime = (int) (entireExamEndTime - System.currentTimeMillis()/1000);
					json.put("entireExamTimeDuration", entireExamAvailableTime);
					JSONObject exam = ViewExams.fetchExam(examId);
					json.put("examTitle", exam.get("title"));
					success = "Attempt already started";
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		else {
			error = "You must log in to start exam";
		}

		PrintWriter out = response.getWriter();
		json.put("success", success);
		json.put("error", error);
		json.put("errorLog", errorLog);
		out.println(json.toString());
	}


}
