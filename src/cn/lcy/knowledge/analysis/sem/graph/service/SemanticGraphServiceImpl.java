package cn.lcy.knowledge.analysis.sem.graph.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeanUtils;

import com.hankcs.hanlp.corpus.dependency.CoNll.CoNLLSentence;
import com.hankcs.hanlp.corpus.dependency.CoNll.CoNLLWord;
import com.hankcs.hanlp.seg.common.Term;

import cn.lcy.knowledge.analysis.enums.NounsTagEnum;
import cn.lcy.knowledge.analysis.enums.VerbsTagEnum;
import cn.lcy.knowledge.analysis.grammar.service.GrammarParserServiceImpl;
import cn.lcy.knowledge.analysis.namedentity.service.NamedEntityServiceImpl;
import cn.lcy.knowledge.analysis.ontology.query.service.QueryServiceImpl;
import cn.lcy.knowledge.analysis.seg.service.WordSegmentationServiceImpl;
import cn.lcy.knowledge.analysis.sem.model.Answer;
import cn.lcy.knowledge.analysis.sem.model.AnswerStatement;
import cn.lcy.knowledge.analysis.sem.model.PolysemantNamedEntity;
import cn.lcy.knowledge.analysis.sem.model.PolysemantStatement;
import cn.lcy.knowledge.analysis.sem.model.QueryResult;
import cn.lcy.knowledge.analysis.sem.model.SemanticGraph;
import cn.lcy.knowledge.analysis.sem.model.SemanticGraphEdge;
import cn.lcy.knowledge.analysis.sem.model.SemanticGraphVertex;
import cn.lcy.knowledge.analysis.sem.model.Word;
import cn.lcy.knowledge.analysis.sem.model.WordSegmentResult;
import cn.lcy.knowledge.analysis.vo.AnswerResultVO;
import cn.lcy.knowledge.analysis.vo.PolysemantSituationVO;
import cn.lcy.knowledge.analysis.vo.SemanticGraphNodeVO;
import cn.lcy.knowledge.analysis.vo.SemanticGraphStatementVO;
import cn.lcy.knowledge.analysis.vo.SemanticGraphVO;

public class SemanticGraphServiceImpl implements SemanticGraphServiceI {

	private volatile static SemanticGraphServiceImpl singleInstance;
	
	/**
	 * 私有化构造方法，实现单例模式
	 */
	private SemanticGraphServiceImpl() {}
	
	/**
	 * 获取单例
	 * @return
	 */
	public static SemanticGraphServiceI getInstance() {
		if (singleInstance == null) {
			synchronized (SemanticGraphServiceImpl.class) {
				if (singleInstance == null) {
					singleInstance = new SemanticGraphServiceImpl();
				}
			}
		}
		return singleInstance;
	}
	
//	/**
//	 * TODO 测试
//	 * @param args
//	 */
	public static void main(String args[]) throws Exception {
		// 第一步：HanLP分词
		WordSegmentResult wordSegmentResult = WordSegmentationServiceImpl.getInstance().wordSegmentation("美人鱼？");
		List<Term> terms = wordSegmentResult.getTerms();
		List<PolysemantNamedEntity> polysemantNamedEntities = wordSegmentResult.getPolysemantEntities();
		List<Word> words = wordSegmentResult.getWords();
		System.out.println("HanLP分词的结果为:" + terms);
		
		// :查询本体库、取出命名实体的相关数据属性和对象属性
		polysemantNamedEntities = NamedEntityServiceImpl.getInstance().fillNamedEntities(polysemantNamedEntities);
		
		// 第二步：使用HanLP进行依存句法分析
		CoNLLSentence coNLLsentence = GrammarParserServiceImpl.getInstance().dependencyParser(terms);
		System.out.println("HanLP依存语法解析结果：\n" + coNLLsentence);
		
		// 第三步：语义图构建
		SemanticGraph semanticGraph = SemanticGraphServiceImpl.getInstance().buildSemanticGraph(coNLLsentence, polysemantNamedEntities);
		if (semanticGraph.getAllVertices().size() == 0) { // 说明没有语义图算法无法解析该问句
			semanticGraph = SemanticGraphServiceImpl.getInstance().buildBackUpSemanticGraph(words);
		}
		
		// 第四步：语义图断言构建
		List<AnswerStatement> semanticStatements = QueryServiceImpl.getInstance().createStatement(semanticGraph);
		
		// 第五步：获取歧义断言
		List<PolysemantStatement> polysemantStatements = QueryServiceImpl.getInstance().createPolysemantStatements(semanticStatements);
		
		List<PolysemantSituationVO> polysemantSituationVOs = new ArrayList<PolysemantSituationVO>();
		
		for (PolysemantStatement polysemantStatement : polysemantStatements) {
			// 第六步：实体消岐
			List<AnswerStatement> individualsDisambiguationStatements = QueryServiceImpl.getInstance().individualsDisambiguation(polysemantStatement.getAnswerStatements());
			List<AnswerStatement> individualsDisambiguationStatementsNew = new ArrayList<AnswerStatement>();
			for (AnswerStatement answerStatement : individualsDisambiguationStatements) {
				Word subject = answerStatement.getSubject();
				Word predicate = answerStatement.getPredicate();
				Word object = answerStatement.getObject();
				Word subjectNew = new Word();
				Word predicateNew = new Word();
				Word objectNew = new Word();
				BeanUtils.copyProperties(subject, subjectNew);
				BeanUtils.copyProperties(predicate, predicateNew);
				BeanUtils.copyProperties(object, objectNew);
				AnswerStatement answerStatementNew = new AnswerStatement();
				answerStatementNew.setSubject(subjectNew);
				answerStatementNew.setPredicate(predicateNew);
				answerStatementNew.setObject(objectNew);
				individualsDisambiguationStatementsNew.add(answerStatementNew);
			}
			
			// 第七步：谓语消岐
			List<AnswerStatement> predicateDisambiguationStatements = QueryServiceImpl.getInstance().predicateDisambiguation(individualsDisambiguationStatementsNew);
			List<AnswerStatement> predicateDisambiguationStatementsNew = new ArrayList<AnswerStatement>();
			for (AnswerStatement answerStatement : predicateDisambiguationStatements) {
				Word subject = answerStatement.getSubject();
				Word predicate = answerStatement.getPredicate();
				Word object = answerStatement.getObject();
				Word subjectNew = new Word();
				Word predicateNew = new Word();
				Word objectNew = new Word();
				BeanUtils.copyProperties(subject, subjectNew);
				BeanUtils.copyProperties(predicate, predicateNew);
				BeanUtils.copyProperties(object, objectNew);
				AnswerStatement answerStatementNew = new AnswerStatement();
				answerStatementNew.setSubject(subjectNew);
				answerStatementNew.setPredicate(predicateNew);
				answerStatementNew.setObject(objectNew);
				predicateDisambiguationStatementsNew.add(answerStatementNew);
			}
			
			// 第八步：构造用于Jena查询的断言
			List<AnswerStatement> queryStatements = QueryServiceImpl.getInstance().createQueryStatements(predicateDisambiguationStatementsNew);

			List<AnswerStatement> queryStatementsNew = new ArrayList<AnswerStatement>();
			for (AnswerStatement answerStatement : queryStatements) {
				Word subject = answerStatement.getSubject();
				Word predicate = answerStatement.getPredicate();
				Word object = answerStatement.getObject();
				Word subjectNew = new Word();
				Word predicateNew = new Word();
				Word objectNew = new Word();
				BeanUtils.copyProperties(subject, subjectNew);
				BeanUtils.copyProperties(predicate, predicateNew);
				BeanUtils.copyProperties(object, objectNew);
				AnswerStatement answerStatementNew = new AnswerStatement();
				answerStatementNew.setSubject(subjectNew);
				answerStatementNew.setPredicate(predicateNew);
				answerStatementNew.setObject(objectNew);
				queryStatementsNew.add(answerStatementNew);
			}
			
			// 第九步：根据查询断言构建查询语句
			List<String> SPARQLS = QueryServiceImpl.getInstance().createSparqls(queryStatementsNew);
			List<QueryResult> queryResults = new ArrayList<QueryResult>();
			for (String SPARQL : SPARQLS) {
				// 执行查询语句
				QueryResult queryResult = QueryServiceImpl.getInstance().queryOntology(SPARQL);
				List<Answer> answersNew = new ArrayList<Answer>();
				for (Answer answer : queryResult.getAnswers()) {
					String[] uuidArr = answer.getContent().split(":");
					String uuid = null;
					if (uuidArr.length > 1) {
						uuid = uuidArr[1];
					} else {
						uuid = uuidArr[0];
					}
					if (uuidArr.length <= 1 || answer.getContent().length() != 33) {
						answersNew.add(answer);
						System.out.println("答案:" + answer.getContent());
					} else {
						String comment = QueryServiceImpl.getInstance().queryIndividualComment(uuid);
						Answer answerNew = new Answer();
						answerNew.setContent(comment);
						answersNew.add(answerNew);
						System.out.println("答案：" + answer.getContent());
					}
				}
				queryResult.setAnswers(answersNew);
				queryResults.add(queryResult);
			}
			
			PolysemantSituationVO polysemantSituationVO = new PolysemantSituationVO();
			polysemantSituationVO.setPolysemantStatement(polysemantStatement);
			polysemantSituationVO.setSemanticStatements(semanticStatements);
			List<PolysemantNamedEntity> activePolysemantNamedEntities = new ArrayList<PolysemantNamedEntity>();
			int index = 0;
			for (AnswerStatement answerStatementNew : polysemantStatement.getAnswerStatements()) {
				PolysemantNamedEntity subjectActivePolysemantNamedEntity = answerStatementNew.getSubject().getActiveEntity();
				PolysemantNamedEntity objectActivePolysemantNamedEntity = answerStatementNew.getObject().getActiveEntity();
				if (index == 0) {
					activePolysemantNamedEntities.add(subjectActivePolysemantNamedEntity);
					activePolysemantNamedEntities.add(objectActivePolysemantNamedEntity);
				} else {
					activePolysemantNamedEntities.add(objectActivePolysemantNamedEntity);
				}
			}
			polysemantSituationVO.setActivePolysemantNamedEntities(activePolysemantNamedEntities);// 激活的命名实体
			polysemantSituationVO.setIndividualsDisambiguationStatements(individualsDisambiguationStatements);
			polysemantSituationVO.setPredicateDisambiguationStatements(predicateDisambiguationStatements);
			polysemantSituationVO.setQueryStatements(queryStatements);
			polysemantSituationVO.setSPARQLS(SPARQLS);
			polysemantSituationVO.setQueryResults(queryResults);
			polysemantSituationVOs.add(polysemantSituationVO);
		}
		AnswerResultVO answerResultVO = new AnswerResultVO(); // 结果的封装
		answerResultVO.setWords(words);
		answerResultVO.setPolysemantSituationVOs(polysemantSituationVOs);
	}
	
	/**
	 * 构建语义图算法
	 */
	public SemanticGraph buildSemanticGraph(CoNLLSentence coNLLsentence, List<PolysemantNamedEntity> polysemantNamedEntities) {
		// 语义图对象
		SemanticGraph semanticGraph = new SemanticGraph();
		// 名词性结点集合
        ArrayList<SemanticGraphVertex> semanticGraphVertexs = new ArrayList<SemanticGraphVertex>();
        
        int index = 0;
        String[] masks = new String[20];
        // 最后处理的边
        List<SemanticGraphEdge> lastProcessEdges = new ArrayList<SemanticGraphEdge>();
        // 迭代所有的单词
		for (CoNLLWord coNLLWord : coNLLsentence) {
			// 如果该结点是名词性结点 isNounWord
			if (this.isNounWord(coNLLWord.CPOSTAG)) {
				// 创建一个语义图结点
				SemanticGraphVertex semanticGraphVertex = new SemanticGraphVertex();
				Word vertexWord = this.convertedIntoWord(coNLLWord, polysemantNamedEntities);
				semanticGraphVertex.setWord(vertexWord);
				// 将名词性结点添加到语义图名词性结点集合
				semanticGraphVertexs.add(semanticGraphVertex);
			}
			
			// 如果该结点是动词性结点
			if (this.isVerbWord(coNLLWord.POSTAG)) {
				// 创建一个语义图边对象
				SemanticGraphEdge semanticGraphEdge = new SemanticGraphEdge();
				Word edgeWord = this.convertedIntoWord(coNLLWord, polysemantNamedEntities);
				semanticGraphEdge.setWord(edgeWord);
				// 主语
				SemanticGraphVertex subjectVertex = null;
				// 宾语
				SemanticGraphVertex objectVertex = null;
				// 迭代所有的依存关系
				for (CoNLLWord coNLLWord2 : coNLLsentence) {
					// 出发节点 word.LEMMA
                    // 依存关系
					// 结束结点 word.HEAD.LEMMA
                    String reln = coNLLWord2.DEPREL;
                    if (coNLLWord.ID == coNLLWord2.HEAD.ID) {
                        switch (reln) {
                        	// 找出该谓语的宾语
                            case "VOB":
                            case "IOB":
                            case "ATT":
                            	// 迭代名词性结点集合
                            	if (this.isInclude(semanticGraphVertexs, coNLLWord2.ID)) {
                            		for (SemanticGraphVertex semanticGraphVertex : semanticGraphVertexs) {
                            			if (semanticGraphVertex.getWord().getPosition() == coNLLWord2.ID) {
                            				objectVertex = semanticGraphVertex;
                            			}
                            		}
                            	} else if (this.isNounWord(coNLLWord2.CPOSTAG)) {
                        			// 如果名词性结点集合中还没有该名词 则创建一个新的名词性结点
                            		objectVertex = new SemanticGraphVertex();
                            		Word vertexWord = this.convertedIntoWord(coNLLWord2, polysemantNamedEntities);
                            		objectVertex.setWord(vertexWord);
                            	}
                                break;
                            // 找出该谓语的主语
                            case "SBV":
                            	if (this.isInclude(semanticGraphVertexs, coNLLWord2.ID)) {
                            		for (SemanticGraphVertex semanticGraphVertex : semanticGraphVertexs) {
                                		if (semanticGraphVertex.getWord().getPosition() == coNLLWord2.ID) {
                                			subjectVertex = semanticGraphVertex;
                                		}
                            		}
                            	}else {
                            		subjectVertex = new SemanticGraphVertex();
                            		Word vertexWord = this.convertedIntoWord(coNLLWord2, polysemantNamedEntities);
                            		subjectVertex.setWord(vertexWord);
                            	}
                                break;
                            // TODO 这是还需要改进
                            case "ADV":
                            	String edgeName = semanticGraphEdge.getWord().getName();
                            	edgeName += coNLLWord2.LEMMA;
                            	semanticGraphEdge.getWord().setName(edgeName);
                            	semanticGraphEdge.getWord().setCpostag("n");
                        }
                    }
                    if (subjectVertex != null && objectVertex != null ) {
                        break;
                    }
                }
				// 如果主语和宾语都为空
				if (subjectVertex == null && objectVertex == null) {
					// 将该主语和宾语都为空的边加入到待处理集合中
					lastProcessEdges.add(semanticGraphEdge);
				}
				else if (subjectVertex == null) {
					subjectVertex = new SemanticGraphVertex();
					// 问号 ID为-1 
					int ch = 'a' + index;
					String mask = "?" + (char)ch;
					System.out.println("符号"+mask);
					masks[index] = mask;
					CoNLLWord maskWord = new CoNLLWord(1024+index, mask, "n");
            		subjectVertex.setWord(this.convertedIntoWord(maskWord, polysemantNamedEntities));
            		++index;
				}
				else if (objectVertex == null) {
					objectVertex = new SemanticGraphVertex();
					// 问号 ID为-1 
					int ch = 'a' + index;
					String mask = "?" + (char)ch;
					System.out.println("符号"+mask);
					masks[index] = mask;
					CoNLLWord maskWord = new CoNLLWord(1024+index, mask, "n");
					objectVertex.setWord(this.convertedIntoWord(maskWord, polysemantNamedEntities));
					 ++index;
				}
                if (subjectVertex != null && objectVertex != null ) {
                	semanticGraph.add(subjectVertex, objectVertex, semanticGraphEdge);
                }
			}
		}
		
		
		if (index > 0 ) {
			// 处理主宾为空的边
			for (SemanticGraphEdge semanticGraphEdge : lastProcessEdges) {
				SemanticGraphVertex subjectVertex = new SemanticGraphVertex();
				SemanticGraphVertex objectVertex = new SemanticGraphVertex();
				int lastMaskIndex = index - 1;
				String lastMask = masks[lastMaskIndex];
				CoNLLWord maskWord = new CoNLLWord(1024+index, lastMask, "n");
				
				int ch = 'a' + index;
				String newLastMask = "?" + (char)ch;
				System.out.println("newLastMask"+newLastMask);
				CoNLLWord newMaskWord = new CoNLLWord(1024+index, newLastMask, "n");
				
				masks[index] = newLastMask;
				System.out.println("lastMask"+lastMask);
				
				subjectVertex.setWord(this.convertedIntoWord(maskWord, polysemantNamedEntities));
				objectVertex.setWord(this.convertedIntoWord(newMaskWord, polysemantNamedEntities));
				semanticGraph.add(subjectVertex, objectVertex, semanticGraphEdge);
				++index;
			}
		}
		
		// 创建修饰名词和被修饰名词之间的联系
		for (CoNLLWord word : coNLLsentence) {
			// 出发节点 word.LEMMA
            // 依存关系
            String reln = word.DEPREL;
            // 结束结点 word.HEAD.LEMMA
            switch (reln) {
            	case "ATT":
            		if (this.isInclude(semanticGraphVertexs, word.ID) && this.isInclude(semanticGraphVertexs, word.HEAD.ID)) {
            			// 词性为名词性变量而不是命名实体
            			if (this.isNounWord(word.HEAD.CPOSTAG)) {
            				SemanticGraphVertex sourceVertex = null;
            				SemanticGraphVertex destVertex = null;
            				SemanticGraphEdge edge = new SemanticGraphEdge();
            				CoNLLWord coNLLWord = word.HEAD;
            				Word edgeWord = this.convertedIntoWord(coNLLWord, polysemantNamedEntities);
            				edge.setWord(edgeWord);
            				// 修饰词结点
            				if (this.isInclude(semanticGraphVertexs, word.ID)) {
            					for (SemanticGraphVertex semanticGraphVertex : semanticGraphVertexs) {
                					if (semanticGraphVertex.getWord().getPosition() == word.ID) {
                						sourceVertex = semanticGraphVertex;
                					}
                				}
            				}
            				// 被修饰词结点
            				if (this.isInclude(semanticGraphVertexs, word.HEAD.ID)) {
            					for (SemanticGraphVertex semanticGraphVertex : semanticGraphVertexs) {
                					if (semanticGraphVertex.getWord().getPosition() == word.HEAD.ID) {
                						destVertex = semanticGraphVertex;
                					}
                				}
            				}
            				// 创建修饰词到被修饰词关联
            				semanticGraph.add(sourceVertex, destVertex, edge);
            			} 
            		}
            }
		}
		semanticGraph.printGraph();
		return semanticGraph;
	}
	
	public SemanticGraph buildBackUpSemanticGraph(List<Word> words) {
		SemanticGraph semanticGraph = new SemanticGraph();
		for (Word word : words) {
			for (PolysemantNamedEntity polysemantNamedEntity : word.getPolysemantNamedEntities()) {
				SemanticGraphVertex subjectVertex = new SemanticGraphVertex();
				subjectVertex.setWord(word);
				
				SemanticGraphEdge semanticGraphEdge = new SemanticGraphEdge();
				Word edgeWordNew = new Word();
				edgeWordNew.setCpostag("n");
				edgeWordNew.setPostag("n");
				edgeWordNew.setName("描述");
				semanticGraphEdge.setWord(edgeWordNew);
				
				SemanticGraphVertex objectVertex = new SemanticGraphVertex();
				Word objectWordNew = new Word();
				objectWordNew.setName("描述");
				List<PolysemantNamedEntity> polysemantNamedEntities = new ArrayList<PolysemantNamedEntity>();
				objectWordNew.setPolysemantNamedEntities(polysemantNamedEntities);
				objectVertex.setWord(objectWordNew);
				
				semanticGraph.add(subjectVertex, objectVertex, semanticGraphEdge);
			}
			
		}
		return semanticGraph;
	}
	
	/**
	 *  判断目前迭代到的结点是否在语义图名词性结点集合中已经存在
	 * @param semanticGraphVertexs
	 * @param index
	 * @return
	 */
	public boolean isInclude(ArrayList<SemanticGraphVertex> semanticGraphVertexs, int index) {
		for (SemanticGraphVertex semanticGraphVertex : semanticGraphVertexs) {
			if (semanticGraphVertex.getWord().getPosition() == index) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * 判断是否是名词
	 * @param tag
	 * @return
	 */
	public boolean isNounWord(String tag) {
		return NounsTagEnum.isIncludeEnumElement(tag);
	}
	
	/**
	 * 判断是否是动词
	 * @param tag
	 * @return
	 */
	public boolean isVerbWord(String tag) {
		return VerbsTagEnum.isIncludeEnumElement(tag);
	}
	
	/**
	 * 获取语义图VO对象
	 */
	@Override
	public SemanticGraphVO getSemanticGraphVO(List<AnswerStatement> queryStatements) {
		// 断言集合
		SemanticGraphVO semanticGraphVO = new SemanticGraphVO();
		// 解析语义图  构造断言
		for (AnswerStatement queryStatement : queryStatements) {
	    		SemanticGraphStatementVO semanticGraphStatementVO = new SemanticGraphStatementVO();
	    		SemanticGraphNodeVO subject = new SemanticGraphNodeVO();
	    		SemanticGraphNodeVO predicate = new SemanticGraphNodeVO();
	    		SemanticGraphNodeVO object = new SemanticGraphNodeVO();
	    		
	    		subject.setID(queryStatement.getSubject().getPosition());
	    		if (queryStatement.getSubject().getName().split(":").length > 1) {
	    			subject.setName(queryStatement.getSubject().getName().split(":")[1]);
	    		} else {
	    			subject.setName(queryStatement.getSubject().getName().split(":")[0]);
	    		}
	    		
	    		subject.setColor("red");
	    		// dot 表示圆形
	    		subject.setShape("dot");
	    		subject.setAlpha(1);
	    		
	    		predicate.setID(queryStatement.getPredicate().getPosition());
	    		predicate.setName(queryStatement.getPredicate().getName());
	    		predicate.setColor("#b2b19d");
	    		predicate.setSize(15);
	    		// 默认为矩形
	    		//predicate.setShape("rect");
	    		predicate.setAlpha(1);
	    		
	    		object.setID(queryStatement.getObject().getPosition());
	    		object.setName(queryStatement.getObject().getName());
	    		object.setColor("#b2b19d");
	    		object.setShape("dot");
	    		object.setAlpha(1);
	    		
	    		semanticGraphStatementVO.setSubject(subject);
	    		semanticGraphStatementVO.setPredicate(predicate);
	    		semanticGraphStatementVO.setObject(object);
	    		semanticGraphVO.getSemanticGraphStatements().add(semanticGraphStatementVO);
	    	}
		return semanticGraphVO;
	}
	
	/**
	 * 将CoNLLWord类型转换成Word类型
	 * @param coNLLWord
	 * @return
	 */
	public Word convertedIntoWord(CoNLLWord coNLLWord, List<PolysemantNamedEntity> polysemantNamedEntities) {
		List<PolysemantNamedEntity> wordPolysemantNamedEntities = new ArrayList<PolysemantNamedEntity>();
		Word word = new Word();
		word.setPosition(coNLLWord.ID);
		word.setName(coNLLWord.LEMMA);
		word.setCpostag(coNLLWord.CPOSTAG);
		word.setPostag(coNLLWord.POSTAG);
		for (PolysemantNamedEntity polysemantNamedEntity : polysemantNamedEntities) {
			if (polysemantNamedEntity.getPosition() == word.getPosition()) {
				wordPolysemantNamedEntities.add(polysemantNamedEntity);
			}
		}
		word.setPolysemantNamedEntities(wordPolysemantNamedEntities);
		return word;
	}
	
}