#!r6rs
(import (only (model cas) normalize)
        (model lang)
        (model record)
        (model formula latex)
        (model formula simpl)
        (chezscheme))

(define (model-list p)
  (let ((s (directory-separator)))
    (filter (lambda (x)
              (and (file-regular? x)
                   (string=? (path-extension x) "ss")))
            (map (lambda (x) (format "~a~a~a" p s x)) (directory-list p)))))

(define (display-equations m)
  (display "<p>$$\\begin{align}\n")
  (for-each
   (lambda (e)
     (display (formula->latex (equation-lhs e)))
     (display " &= ")
     (display (formula->latex (equation-rhs e)))
     (display "\\\\\n"))
   (model-equations m))
  (display "\\end{align}$$</p>\n"))

(define (equation-simplify e)
  (make-equation (formula-simplify (equation-lhs e))
                 (formula-simplify (equation-rhs e))))

(define (display-example p)
  (load p)
  (let* ((name (path-last (path-root p)))
         (m (eval (string->symbol name))))
    (display "<h1>")
    (display name)
    (display "</h1>\n")
    (display "<h2>Defining equations</h2>\n")
    (display-equations m)
    (display "<h2>System of 1st-order differential equations</h2>\n")
    (let ((n (normalize m)))
      (display-equations (make-model (model-variables n)
                                     (map equation-simplify (model-equations n)))))
    ))

(let ((p (cadr (command-line))))
  (display "<!DOCTYPE html>\n")
  (display "<html>\n")
  (display "<head>\n")
  (display "<script src=\"https://polyfill.io/v3/polyfill.min.js?features=es6\"></script>\n")
  (display "<script id=\"MathJax-script\" async src=\"https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js\"></script>\n")
  (display "</head>\n")
  (display "<body>\n")
  (for-each display-example (list-sort string<? (model-list p)))
  (display "</body>\n")
  (display "</html>\n"))
